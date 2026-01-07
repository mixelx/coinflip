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

import java.util.List;
import java.util.UUID;

@Controller("/api")
public class ApiController {

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
    }

    @Get("/config")
    public ConfigResponse getConfig() {
        return new ConfigResponse(
                appConfig.getDeposit().getTonAddress(),
                appConfig.getTon().getNetwork()
        );
    }

    @Get("/state")
    public StateResponse getState(HttpRequest<?> request) {
        long telegramUserId = telegramUserHelper.requireTelegramUserId(request);
        UUID userId = userService.getOrCreateUserId(telegramUserId);

        BalanceDto balance = balanceService.getBalance(userId);

        List<GameDto> games = coinflipGameRepository.findTop20ByUserIdOrderByCreatedAtDesc(userId)
                .stream()
                .map(this::toGameDto)
                .toList();

        List<WithdrawDto> withdraws = withdrawRequestRepository.findTop20ByUserIdOrderByCreatedAtDesc(userId)
                .stream()
                .map(this::toWithdrawDto)
                .toList();

        return new StateResponse(
                balance.tonNano(),
                balance.usdtMicro(),
                games,
                withdraws
        );
    }

    @Post("/coinflip")
    public CoinflipResponse coinflip(HttpRequest<?> request, @Body CoinflipRequest body) {
        long telegramUserId = telegramUserHelper.requireTelegramUserId(request);
        UUID userId = userService.getOrCreateUserId(telegramUserId);

        CoinflipGameEntity game = coinflipService.play(userId, body.side(), body.stakeNano());

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
        long telegramUserId = telegramUserHelper.requireTelegramUserId(request);
        UUID userId = userService.getOrCreateUserId(telegramUserId);

        DepositEntity deposit = depositService.recordDeposit(userId, body.asset(), body.amount(), null);

        return new DepositResponse(deposit.getId(), deposit.getAsset(), deposit.getAmount());
    }

    @Post("/deposit/claim")
    public HttpResponse<DepositClaimResponse> claimDeposit(HttpRequest<?> request, @Body DepositClaimRequest body) {
        long telegramUserId = telegramUserHelper.requireTelegramUserId(request);
        UUID userId = userService.getOrCreateUserId(telegramUserId);

        DepositClaimService.ClaimResult result = depositClaimService.claimDeposit(userId, body.amountNano());

        DepositClaimResponse response = new DepositClaimResponse(
                result.status(),
                result.depositId(),
                result.txHash(),
                result.newTonBalanceNano()
        );

        if ("CONFIRMED".equals(result.status())) {
            return HttpResponse.ok(response);
        } else {
            return HttpResponse.status(HttpStatus.ACCEPTED).body(response);
        }
    }

    @Get("/deposit/status")
    public DepositStatusResponse getDepositStatus(@QueryValue UUID depositId) {
        DepositEntity deposit = depositClaimService.getDepositStatus(depositId)
                .orElseThrow(() -> new BadRequestException("Deposit not found: " + depositId));

        return new DepositStatusResponse(
                deposit.getId(),
                deposit.getStatus(),
                deposit.getTxHash()
        );
    }

    @Post("/withdraw")
    public WithdrawResponse withdraw(HttpRequest<?> request, @Body WithdrawRequest body) {
        long telegramUserId = telegramUserHelper.requireTelegramUserId(request);
        UUID userId = userService.getOrCreateUserId(telegramUserId);

        WithdrawRequestEntity wr = withdrawService.createWithdrawRequest(
                userId, body.asset(), body.amount(), body.toAddress()
        );

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
