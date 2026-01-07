package com.example.controller;

import com.example.config.AppConfig;
import com.example.controller.dto.*;
import com.example.db.entity.CoinflipGameEntity;
import com.example.db.entity.DepositEntity;
import com.example.db.entity.WithdrawRequestEntity;
import com.example.db.repo.CoinflipGameRepository;
import com.example.db.repo.WithdrawRequestRepository;
import com.example.exception.BadRequestException;
import com.example.service.*;
import io.micronaut.http.HttpRequest;
import io.micronaut.http.HttpResponse;
import io.micronaut.http.HttpStatus;
import io.micronaut.http.annotation.*;
import io.micronaut.scheduling.TaskExecutors;
import io.micronaut.scheduling.annotation.ExecuteOn;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.List;
import java.util.UUID;

@Controller("/api")
public class ApiController {

    private static final Logger LOG = LoggerFactory.getLogger(ApiController.class);

    private final AppConfig appConfig;
    private final TelegramUserHelper telegramUserHelper;
    private final UserService userService;
    private final BalanceService balanceService;
    private final CoinflipService coinflipService;
    private final DepositService depositService;
    private final DepositClaimService depositClaimService;
    private final WithdrawService withdrawService;
    private final CoinflipGameRepository coinflipGameRepository;
    private final WithdrawRequestRepository withdrawRequestRepository;

    public ApiController(
            AppConfig appConfig,
            TelegramUserHelper telegramUserHelper,
            UserService userService,
            BalanceService balanceService,
            CoinflipService coinflipService,
            DepositService depositService,
            DepositClaimService depositClaimService,
            WithdrawService withdrawService,
            CoinflipGameRepository coinflipGameRepository,
            WithdrawRequestRepository withdrawRequestRepository
    ) {
        this.appConfig = appConfig;
        this.telegramUserHelper = telegramUserHelper;
        this.userService = userService;
        this.balanceService = balanceService;
        this.coinflipService = coinflipService;
        this.depositService = depositService;
        this.depositClaimService = depositClaimService;
        this.withdrawService = withdrawService;
        this.coinflipGameRepository = coinflipGameRepository;
        this.withdrawRequestRepository = withdrawRequestRepository;
        LOG.debug("ApiController initialized");
    }

    @Get("/config")
    public ConfigResponse getConfig() {
        LOG.debug("GET /api/config");
        ConfigResponse response = new ConfigResponse(
                appConfig.getDeposit().getTonAddress(),
                appConfig.getTon().getNetwork()
        );
        LOG.debug("GET /api/config -> depositAddress={}, network={}", 
                response.depositTonAddress(), response.network());
        return response;
    }

    @Get("/state")
    public StateResponse getState(HttpRequest<?> request) {
        LOG.debug("GET /api/state");
        long telegramUserId = telegramUserHelper.requireTelegramUserId(request);
        LOG.debug("GET /api/state: telegramUserId={}", telegramUserId);
        
        UUID userId = userService.getOrCreateUserId(telegramUserId);
        LOG.debug("GET /api/state: userId={}", userId);

        BalanceDto balance = balanceService.getBalance(userId);
        LOG.debug("GET /api/state: balance tonNano={}, usdtMicro={}", 
                balance.tonNano(), balance.usdtMicro());

        List<GameDto> games = coinflipGameRepository.findTop20ByUserIdOrderByCreatedAtDesc(userId)
                .stream()
                .map(this::toGameDto)
                .toList();
        LOG.debug("GET /api/state: loaded {} games", games.size());

        List<WithdrawDto> withdraws = withdrawRequestRepository.findTop20ByUserIdOrderByCreatedAtDesc(userId)
                .stream()
                .map(this::toWithdrawDto)
                .toList();
        LOG.debug("GET /api/state: loaded {} withdraws", withdraws.size());

        return new StateResponse(
                balance.tonNano(),
                balance.usdtMicro(),
                games,
                withdraws
        );
    }

    @Post("/coinflip")
    public CoinflipResponse coinflip(HttpRequest<?> request, @Body CoinflipRequest body) {
        LOG.debug("POST /api/coinflip: side={}, stakeNano={}", body.side(), body.stakeNano());
        
        long telegramUserId = telegramUserHelper.requireTelegramUserId(request);
        UUID userId = userService.getOrCreateUserId(telegramUserId);
        LOG.debug("POST /api/coinflip: userId={}", userId);

        CoinflipGameEntity game = coinflipService.play(userId, body.side(), body.stakeNano());
        LOG.debug("POST /api/coinflip: result={}, win={}", game.getResultSide(), game.getWin());

        return new CoinflipResponse(
                game.getId(),
                game.getChosenSide(),
                game.getResultSide(),
                game.getWin(),
                game.getStakeNano()
        );
    }

    @Post("/deposit")
    public DepositResponse deposit(HttpRequest<?> request, @Body DepositRequest body) {
        LOG.debug("POST /api/deposit: asset={}, amount={}", body.asset(), body.amount());
        
        long telegramUserId = telegramUserHelper.requireTelegramUserId(request);
        UUID userId = userService.getOrCreateUserId(telegramUserId);
        LOG.debug("POST /api/deposit: userId={}", userId);

        DepositEntity deposit = depositService.recordDeposit(userId, body.asset(), body.amount(), null);
        LOG.debug("POST /api/deposit: created depositId={}", deposit.getId());

        return new DepositResponse(deposit.getId(), deposit.getAsset(), deposit.getAmount());
    }

    @Post("/deposit/claim")
    @ExecuteOn(TaskExecutors.BLOCKING)
    public HttpResponse<DepositClaimResponse> claimDeposit(HttpRequest<?> request, @Body DepositClaimRequest body) {
        LOG.info("=== POST /api/deposit/claim START ===");
        LOG.info("Request: amountNano={}, fromAddress={}", body.amountNano(), body.fromAddress());
        
        long telegramUserId = telegramUserHelper.requireTelegramUserId(request);
        LOG.debug("telegramUserId={}", telegramUserId);
        
        UUID userId = userService.getOrCreateUserId(telegramUserId);
        LOG.debug("userId={}", userId);

        LOG.debug("Calling depositClaimService.claimDeposit...");
        DepositClaimService.ClaimResult result = depositClaimService.claimDeposit(
                userId, 
                body.amountNano(), 
                body.fromAddress()
        );
        LOG.info("claimDeposit result: status={}, depositId={}, txHash={}", 
                result.status(), result.depositId(), result.txHash());

        DepositClaimResponse response = new DepositClaimResponse(
                result.status(),
                result.depositId(),
                result.txHash(),
                result.newTonBalanceNano()
        );

        if ("CONFIRMED".equals(result.status())) {
            LOG.info("=== POST /api/deposit/claim END: 200 OK (CONFIRMED) ===");
            return HttpResponse.ok(response);
        } else {
            LOG.info("=== POST /api/deposit/claim END: 202 ACCEPTED (PENDING) ===");
            return HttpResponse.status(HttpStatus.ACCEPTED).body(response);
        }
    }

    @Post("/deposit/verify")
    @ExecuteOn(TaskExecutors.BLOCKING)
    public DepositVerifyResponse verifyDeposit(HttpRequest<?> request, @Body DepositVerifyRequest body) {
        LOG.info("=== POST /api/deposit/verify START ===");
        LOG.info("Request: depositId={}", body.depositId());
        
        long telegramUserId = telegramUserHelper.requireTelegramUserId(request);
        LOG.debug("telegramUserId={}", telegramUserId);
        
        UUID userId = userService.getOrCreateUserId(telegramUserId);
        LOG.debug("userId={}", userId);

        // Verify deposit belongs to this user
        LOG.debug("Checking deposit ownership...");
        if (!depositClaimService.isDepositOwnedByUser(body.depositId(), userId)) {
            LOG.warn("Deposit {} not found or access denied for user {}", body.depositId(), userId);
            throw new BadRequestException("Deposit not found or access denied: " + body.depositId());
        }
        LOG.debug("Ownership verified");

        // Perform verification
        LOG.debug("Calling depositClaimService.verifyPendingDeposit...");
        DepositClaimService.VerifyResult result = depositClaimService.verifyPendingDeposit(body.depositId());
        LOG.info("verifyPendingDeposit result: status={}, txHash={}, balance={}", 
                result.status(), result.txHash(), result.tonBalanceNano());

        DepositVerifyResponse response = new DepositVerifyResponse(
                body.depositId(),
                result.status(),
                result.txHash(),
                result.tonBalanceNano() != null ? result.tonBalanceNano() : 0L
        );
        
        LOG.info("=== POST /api/deposit/verify END: status={} ===", result.status());
        return response;
    }

    @Get("/deposit/status")
    public DepositStatusResponse getDepositStatus(@QueryValue UUID depositId) {
        LOG.debug("GET /api/deposit/status: depositId={}", depositId);
        
        DepositEntity deposit = depositClaimService.getDepositStatus(depositId)
                .orElseThrow(() -> {
                    LOG.warn("Deposit not found: {}", depositId);
                    return new BadRequestException("Deposit not found: " + depositId);
                });

        LOG.debug("GET /api/deposit/status: status={}, txHash={}", 
                deposit.getStatus(), deposit.getTxHash());
        
        return new DepositStatusResponse(
                deposit.getId(),
                deposit.getStatus(),
                deposit.getTxHash()
        );
    }

    @Post("/withdraw")
    public WithdrawResponse withdraw(HttpRequest<?> request, @Body WithdrawRequest body) {
        LOG.debug("POST /api/withdraw: asset={}, amount={}, toAddress={}", 
                body.asset(), body.amount(), body.toAddress());
        
        long telegramUserId = telegramUserHelper.requireTelegramUserId(request);
        UUID userId = userService.getOrCreateUserId(telegramUserId);
        LOG.debug("POST /api/withdraw: userId={}", userId);

        WithdrawRequestEntity wr = withdrawService.createWithdrawRequest(
                userId, body.asset(), body.amount(), body.toAddress()
        );
        LOG.debug("POST /api/withdraw: created withdrawId={}", wr.getId());

        return new WithdrawResponse(wr.getId(), wr.getAsset(), wr.getAmount(), wr.getToAddress(), wr.getStatus());
    }

    private GameDto toGameDto(CoinflipGameEntity entity) {
        return new GameDto(
                entity.getId(),
                entity.getStakeNano(),
                entity.getChosenSide(),
                entity.getResultSide(),
                entity.getWin(),
                entity.getCreatedAt()
        );
    }

    private WithdrawDto toWithdrawDto(WithdrawRequestEntity entity) {
        return new WithdrawDto(
                entity.getId(),
                entity.getAsset(),
                entity.getAmount(),
                entity.getToAddress(),
                entity.getStatus(),
                entity.getCreatedAt()
        );
    }
}
