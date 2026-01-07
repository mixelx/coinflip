// === TonConnect UI Setup ===
let tonConnectUI = null;
let currentWalletAddress = null;
let telegramUserId = null;

// For testing without Telegram
function getTelegramUserId() {
    if (window.Telegram?.WebApp?.initDataUnsafe?.user?.id) {
        return window.Telegram.WebApp.initDataUnsafe.user.id;
    }
    let storedId = localStorage.getItem('test_tg_user_id');
    if (!storedId) {
        storedId = Math.floor(Math.random() * 1000000000).toString();
        localStorage.setItem('test_tg_user_id', storedId);
    }
    return parseInt(storedId);
}

// === Config (from backend) ===
let appConfig = {
    depositTonAddress: null,
    network: 'mainnet'
};

// === State ===
let state = {
    tonBalanceNano: 0,
    usdtBalanceMicro: 0,
    games: [],
    withdraws: [],
    selectedSide: 'HEADS',
    depositAsset: 'TON',
    withdrawAsset: 'TON',
    activeTab: 'game'
};

// === Initialize ===
document.addEventListener('DOMContentLoaded', async () => {
    telegramUserId = getTelegramUserId();
    console.log('Telegram User ID:', telegramUserId);
    
    initTonConnect();
    initEventListeners();
    await loadConfig();
    await loadState();
});

async function loadConfig() {
    try {
        const config = await fetch('/api/config').then(r => r.json());
        appConfig = config;
        console.log('Config loaded:', config);
    } catch (error) {
        console.error('Failed to load config:', error);
    }
}

function initTonConnect() {
    try {
        // Use absolute URL for manifest - required by some wallets
        const manifestUrl = window.location.origin + '/tonconnect-manifest.json';
        console.log('TonConnect manifest URL:', manifestUrl);
        
        tonConnectUI = new TON_CONNECT_UI.TonConnectUI({
            manifestUrl: manifestUrl,
            buttonRootId: null
        });

        tonConnectUI.onStatusChange(wallet => {
            if (wallet) {
                onWalletConnected(wallet);
            } else {
                onWalletDisconnected();
            }
        });

        if (tonConnectUI.wallet) {
            onWalletConnected(tonConnectUI.wallet);
        }
        
        console.log('TonConnect initialized successfully');
    } catch (error) {
        console.error('Failed to initialize TonConnect:', error);
        alert('Failed to initialize wallet connection: ' + error.message);
    }
}

function initEventListeners() {
    // Connect wallet button
    document.getElementById('connect-btn').addEventListener('click', handleConnectWallet);
    
    // Tab navigation
    document.querySelectorAll('.tab').forEach(tab => {
        tab.addEventListener('click', () => switchTab(tab.dataset.tab));
    });
    
    // Coinflip side selection
    document.querySelectorAll('.coinflip__side-btn').forEach(btn => {
        btn.addEventListener('click', () => selectSide(btn.dataset.side));
    });
    
    // Flip button
    document.getElementById('flip-btn').addEventListener('click', handleFlip);
    
    // Deposit asset selection
    document.querySelectorAll('.deposit__asset-btn').forEach(btn => {
        btn.addEventListener('click', () => selectDepositAsset(btn.dataset.asset));
    });
    
    // Deposit confirm button
    document.getElementById('deposit-confirm-btn').addEventListener('click', handleDepositConfirm);
    
    // Withdraw asset selection
    document.querySelectorAll('.withdraw__asset-btn').forEach(btn => {
        btn.addEventListener('click', () => selectWithdrawAsset(btn.dataset.asset));
    });
    
    // Withdraw button
    document.getElementById('withdraw-btn').addEventListener('click', handleWithdraw);
}

// === Tab Navigation ===
function switchTab(tabId) {
    state.activeTab = tabId;
    
    // Update tab buttons
    document.querySelectorAll('.tab').forEach(tab => {
        tab.classList.toggle('tab--active', tab.dataset.tab === tabId);
    });
    
    // Update tab content
    document.querySelectorAll('.tab-content').forEach(content => {
        content.classList.toggle('tab-content--active', content.id === `tab-${tabId}`);
    });
}

// === Wallet Connection ===
async function handleConnectWallet() {
    if (!tonConnectUI) {
        alert('TonConnect not initialized');
        return;
    }
    
    if (tonConnectUI.wallet) {
        await tonConnectUI.disconnect();
    } else {
        await tonConnectUI.openModal();
    }
}

function onWalletConnected(wallet) {
    currentWalletAddress = wallet.account.address;
    const friendlyAddress = formatAddress(currentWalletAddress);
    
    document.getElementById('wallet-status').classList.add('wallet-status--connected');
    document.getElementById('wallet-status').classList.remove('wallet-status--disconnected');
    document.querySelector('.wallet-status__text').textContent = 'Connected';
    document.getElementById('connect-btn').querySelector('span').textContent = 'Disconnect';
    
    document.getElementById('wallet-info').classList.remove('hidden');
    document.getElementById('wallet-address').textContent = friendlyAddress;
    // Deposit address comes from backend config, not wallet
    
    console.log('Wallet connected:', friendlyAddress);
}

function onWalletDisconnected() {
    currentWalletAddress = null;
    
    document.getElementById('wallet-status').classList.remove('wallet-status--connected');
    document.getElementById('wallet-status').classList.add('wallet-status--disconnected');
    document.querySelector('.wallet-status__text').textContent = 'Not connected';
    document.getElementById('connect-btn').querySelector('span').textContent = 'Connect Wallet';
    
    document.getElementById('wallet-info').classList.add('hidden');
    document.getElementById('wallet-address').textContent = '—';
    // Deposit address stays from config
    
    console.log('Wallet disconnected');
}

function formatAddress(address) {
    if (!address) return '—';
    if (address.length > 20) {
        return address.slice(0, 6) + '...' + address.slice(-4);
    }
    return address;
}

// === API Calls ===
async function apiCall(endpoint, options = {}) {
    const headers = {
        'Content-Type': 'application/json',
        'X-Tg-UserId': telegramUserId.toString()
    };
    
    const response = await fetch(endpoint, {
        ...options,
        headers: { ...headers, ...options.headers }
    });
    
    if (!response.ok) {
        const error = await response.json().catch(() => ({ message: 'Request failed' }));
        throw new Error(error.message || 'Request failed');
    }
    
    return response.json();
}

async function loadState() {
    try {
        const data = await apiCall('/api/state');
        state = { ...state, ...data };
        updateUI();
    } catch (error) {
        console.error('Failed to load state:', error);
    }
}

// === UI Updates ===
function updateUI() {
    // Update balances (compact format for header)
    document.getElementById('ton-balance').textContent = formatTonCompact(state.tonBalanceNano);
    document.getElementById('usdt-balance').textContent = formatUsdtCompact(state.usdtBalanceMicro);
    
    updateGamesList();
    updateWithdrawsList();
}

function formatTon(nano) {
    return (nano / 1_000_000_000).toFixed(9);
}

function formatTonCompact(nano) {
    const ton = nano / 1_000_000_000;
    if (ton >= 1000) return (ton / 1000).toFixed(1) + 'K';
    if (ton >= 1) return ton.toFixed(2);
    if (ton >= 0.001) return ton.toFixed(3);
    return ton.toFixed(4);
}

function formatUsdt(micro) {
    return (micro / 1_000_000).toFixed(6);
}

function formatUsdtCompact(micro) {
    const usdt = micro / 1_000_000;
    if (usdt >= 1000) return (usdt / 1000).toFixed(1) + 'K';
    if (usdt >= 1) return usdt.toFixed(2);
    return usdt.toFixed(3);
}

function updateGamesList() {
    const container = document.getElementById('games-list');
    
    if (!state.games || state.games.length === 0) {
        container.innerHTML = '<div class="history-list__empty">No games yet</div>';
        return;
    }
    
    container.innerHTML = state.games.map(game => `
        <div class="history-item">
            <div class="history-item__main">
                <span class="history-item__title">${game.chosenSide} → ${game.resultSide}</span>
                <span class="history-item__subtitle">${formatTonCompact(game.stakeNano)} TON</span>
            </div>
            <span class="history-item__badge history-item__badge--${game.win ? 'win' : 'lose'}">
                ${game.win ? 'WIN' : 'LOSE'}
            </span>
        </div>
    `).join('');
}

function updateWithdrawsList() {
    const container = document.getElementById('withdraws-list');
    
    if (!state.withdraws || state.withdraws.length === 0) {
        container.innerHTML = '<div class="history-list__empty">No withdraw requests</div>';
        return;
    }
    
    container.innerHTML = state.withdraws.map(w => `
        <div class="history-item">
            <div class="history-item__main">
                <span class="history-item__title">${w.asset === 'TON' ? formatTonCompact(w.amount) : formatUsdtCompact(w.amount)} ${w.asset}</span>
                <span class="history-item__subtitle">${formatAddress(w.toAddress)}</span>
            </div>
            <span class="history-item__badge history-item__badge--pending">${w.status}</span>
        </div>
    `).join('');
}

// === Coinflip ===
function selectSide(side) {
    state.selectedSide = side;
    
    document.querySelectorAll('.coinflip__side-btn').forEach(btn => {
        btn.classList.toggle('coinflip__side-btn--active', btn.dataset.side === side);
    });
}

async function handleFlip() {
    const stakeInput = document.getElementById('stake-input');
    const stakeTon = parseFloat(stakeInput.value);
    
    if (!stakeTon || stakeTon <= 0) {
        alert('Please enter a valid stake');
        return;
    }
    
    // Convert TON to nano (1 TON = 1,000,000,000 nano)
    const stakeNano = Math.floor(stakeTon * 1_000_000_000);
    
    if (stakeNano < 1_000_000) { // Minimum 0.001 TON
        alert('Minimum stake is 0.001 TON');
        return;
    }
    
    const flipBtn = document.getElementById('flip-btn');
    const coin = document.getElementById('coin');
    const resultDiv = document.getElementById('flip-result');
    
    flipBtn.disabled = true;
    resultDiv.classList.add('hidden');
    
    coin.classList.add('flipping');
    
    try {
        const result = await apiCall('/api/coinflip', {
            method: 'POST',
            body: JSON.stringify({
                side: state.selectedSide,
                stakeNano: stakeNano
            })
        });
        
        await new Promise(resolve => setTimeout(resolve, 1000));
        
        coin.classList.remove('flipping');
        coin.classList.toggle('result-tails', result.resultSide === 'TAILS');
        
        resultDiv.classList.remove('hidden', 'coinflip__result--win', 'coinflip__result--lose');
        resultDiv.classList.add(result.win ? 'coinflip__result--win' : 'coinflip__result--lose');
        
        // Display result in TON (user-friendly format)
        resultDiv.querySelector('.coinflip__result-text').textContent = 
            result.win ? `🎉 Won ${stakeTon} TON!` : `😢 Lost ${stakeTon} TON`;
        
        await loadState();
        
    } catch (error) {
        coin.classList.remove('flipping');
        alert(error.message);
    } finally {
        flipBtn.disabled = false;
    }
}

// === Deposit ===
function selectDepositAsset(asset) {
    state.depositAsset = asset;
    
    document.querySelectorAll('.deposit__asset-btn').forEach(btn => {
        btn.classList.toggle('deposit__asset-btn--active', btn.dataset.asset === asset);
    });
    
    // Update UI labels based on selected asset
    const amountLabel = document.getElementById('deposit-amount-label');
    const depositBtn = document.getElementById('deposit-confirm-btn');
    const hint = document.getElementById('deposit-hint');
    
    if (asset === 'TON') {
        amountLabel.textContent = 'Amount (TON)';
        depositBtn.querySelector('span').textContent = '💎 Deposit TON';
        hint.textContent = 'Send from your connected wallet via TonConnect';
    } else {
        amountLabel.textContent = 'Amount (USDT)';
        depositBtn.querySelector('span').textContent = '💵 Deposit USDT';
        hint.textContent = 'Send USDT (TON network) from your connected wallet';
    }
}

// Handle deposit button click
async function handleDepositConfirm() {
    const amount = document.getElementById('deposit-amount').value;
    
    if (!amount || parseFloat(amount) <= 0) {
        alert('Please enter a valid amount');
        return;
    }
    
    if (state.depositAsset === 'TON') {
        await handleDepositTon();
    } else {
        await handleDepositUsdt();
    }
}

// Real TON deposit with TonConnect
async function handleDepositTon() {
    const amountInput = document.getElementById('deposit-amount');
    const amount = parseFloat(amountInput.value);
    
    if (!amount || amount <= 0) {
        alert('Please enter a valid amount');
        return;
    }
    
    if (!tonConnectUI || !tonConnectUI.wallet) {
        alert('Please connect your wallet first');
        return;
    }
    
    if (!appConfig.depositTonAddress) {
        alert('Deposit address not configured');
        return;
    }
    
    const amountNano = Math.floor(amount * 1_000_000_000);
    const depositBtn = document.getElementById('deposit-confirm-btn');
    const originalText = depositBtn.querySelector('span').textContent;
    
    try {
        depositBtn.disabled = true;
        depositBtn.querySelector('span').textContent = 'Sending...';
        
        // Create TonConnect transaction
        const transaction = {
            validUntil: Math.floor(Date.now() / 1000) + 300, // 5 minutes
            messages: [
                {
                    address: appConfig.depositTonAddress,
                    amount: amountNano.toString()
                }
            ]
        };
        
        console.log('Sending transaction:', transaction);
        
        // Send transaction via TonConnect
        await tonConnectUI.sendTransaction(transaction);
        
        console.log('Transaction sent, claiming deposit...');
        depositBtn.querySelector('span').textContent = 'Confirming...';
        
        // Wait a bit for transaction to propagate
        await new Promise(resolve => setTimeout(resolve, 3000));
        
        // Claim the deposit
        const claimResult = await claimDeposit(amountNano);
        
        if (claimResult.status === 'CONFIRMED') {
            alert(`✅ Deposit confirmed! New balance: ${formatTonCompact(claimResult.newTonBalanceNano)} TON`);
            amountInput.value = '';
            await loadState();
        } else {
            // Start polling
            depositBtn.querySelector('span').textContent = 'Waiting for confirmation...';
            const confirmed = await pollDepositStatus(claimResult.depositId);
            
            if (confirmed) {
                alert('✅ Deposit confirmed!');
                amountInput.value = '';
                await loadState();
            } else {
                alert('⏳ Deposit is pending. It may take a few minutes to confirm.');
            }
        }
        
    } catch (error) {
        console.error('Deposit failed:', error);
        if (error.message?.includes('User rejected')) {
            alert('Transaction cancelled');
        } else {
            alert('Deposit failed: ' + (error.message || 'Unknown error'));
        }
    } finally {
        depositBtn.disabled = false;
        depositBtn.querySelector('span').textContent = originalText;
    }
}

// Real USDT deposit with TonConnect (Jetton transfer)
async function handleDepositUsdt() {
    const amountInput = document.getElementById('deposit-amount');
    const amount = parseFloat(amountInput.value);
    
    if (!amount || amount <= 0) {
        alert('Please enter a valid amount');
        return;
    }
    
    if (!tonConnectUI || !tonConnectUI.wallet) {
        alert('Please connect your wallet first');
        return;
    }
    
    if (!appConfig.depositTonAddress) {
        alert('Deposit address not configured');
        return;
    }
    
    if (!appConfig.usdtJettonMaster) {
        alert('USDT not configured');
        return;
    }
    
    // USDT has 6 decimals
    const amountMicro = Math.floor(amount * 1_000_000);
    const depositBtn = document.getElementById('deposit-confirm-btn');
    const originalText = depositBtn.querySelector('span').textContent;
    
    try {
        depositBtn.disabled = true;
        depositBtn.querySelector('span').textContent = 'Preparing...';
        
        // Get user's Jetton wallet address for USDT
        const userAddress = tonConnectUI.wallet.account.address;
        console.log('User wallet address:', userAddress);
        
        // Fetch user's USDT Jetton wallet address
        depositBtn.querySelector('span').textContent = 'Getting wallet...';
        const jettonWalletAddress = await getJettonWalletAddress(userAddress, appConfig.usdtJettonMaster);
        
        if (!jettonWalletAddress) {
            alert('Could not find your USDT wallet. Make sure you have USDT in your wallet.');
            return;
        }
        
        console.log('User Jetton wallet:', jettonWalletAddress);
        
        // Build Jetton transfer payload
        depositBtn.querySelector('span').textContent = 'Building transaction...';
        const transferPayload = await buildJettonTransferPayload(
            amountMicro,
            appConfig.depositTonAddress,
            userAddress  // response destination (refund address)
        );
        
        depositBtn.querySelector('span').textContent = 'Sending...';
        
        // Create TonConnect transaction for Jetton transfer
        // We send to user's Jetton wallet, not the master contract
        const transaction = {
            validUntil: Math.floor(Date.now() / 1000) + 300,
            messages: [
                {
                    address: jettonWalletAddress,
                    amount: "100000000", // 0.1 TON for gas
                    payload: transferPayload
                }
            ]
        };
        
        console.log('Sending Jetton transfer:', transaction);
        
        await tonConnectUI.sendTransaction(transaction);
        
        console.log('Jetton transfer sent, claiming deposit...');
        depositBtn.querySelector('span').textContent = 'Confirming...';
        
        // Wait for transaction to propagate
        await new Promise(resolve => setTimeout(resolve, 5000));
        
        // Claim the USDT deposit
        const claimResult = await claimUsdtDeposit(amountMicro);
        
        if (claimResult.status === 'CONFIRMED') {
            alert(`✅ USDT Deposit confirmed! New balance: ${formatUsdtCompact(claimResult.newUsdtBalanceMicro)} USDT`);
            amountInput.value = '';
            await loadState();
        } else {
            // Start polling
            depositBtn.querySelector('span').textContent = 'Waiting for confirmation...';
            const confirmed = await pollDepositStatus(claimResult.depositId);
            
            if (confirmed) {
                alert('✅ USDT Deposit confirmed!');
                amountInput.value = '';
                await loadState();
            } else {
                alert('⏳ Deposit is pending. It may take a few minutes to confirm.');
            }
        }
        
    } catch (error) {
        console.error('USDT Deposit failed:', error);
        if (error.message?.includes('User rejected')) {
            alert('Transaction cancelled');
        } else {
            alert('USDT Deposit failed: ' + (error.message || 'Unknown error'));
        }
    } finally {
        depositBtn.disabled = false;
        depositBtn.querySelector('span').textContent = originalText;
    }
}

// Get user's Jetton wallet address for a specific Jetton master
async function getJettonWalletAddress(ownerAddress, jettonMasterAddress) {
    console.log('getJettonWalletAddress called:', { ownerAddress, jettonMasterAddress });
    
    try {
        // Convert raw address (0:abc...) to friendly format if needed
        const friendlyOwner = await convertToFriendlyAddress(ownerAddress);
        console.log('Friendly owner address:', friendlyOwner);
        
        // Use Toncenter API v3 to get Jetton wallets
        const url = `https://toncenter.com/api/v3/jetton/wallets?` +
            `owner_address=${encodeURIComponent(friendlyOwner)}&` +
            `jetton_master=${encodeURIComponent(jettonMasterAddress)}&` +
            `limit=1`;
        
        console.log('Fetching Jetton wallet from:', url);
        
        const response = await fetch(url);
        const data = await response.json();
        console.log('Jetton wallets API response:', data);
        
        if (data.jetton_wallets && data.jetton_wallets.length > 0) {
            const walletAddress = data.jetton_wallets[0].address;
            console.log('Found Jetton wallet:', walletAddress);
            return walletAddress;
        }
        
        console.warn('No Jetton wallet found for owner');
        return null;
    } catch (error) {
        console.error('Failed to get Jetton wallet address:', error);
        return null;
    }
}

// Convert raw TON address (0:abc...) to friendly format (EQ.../UQ...)
async function convertToFriendlyAddress(address) {
    if (!address) return address;
    
    // If already friendly format (starts with E, U, etc.), return as is
    if (!address.includes(':')) {
        return address;
    }
    
    try {
        // Use Toncenter API to convert address
        const response = await fetch(
            `https://toncenter.com/api/v2/packAddress?address=${encodeURIComponent(address)}`
        );
        const data = await response.json();
        console.log('packAddress response:', data);
        
        if (data.ok && data.result) {
            return data.result;
        }
    } catch (error) {
        console.error('Failed to convert address:', error);
    }
    
    // Fallback: return original
    return address;
}

// Build Jetton transfer payload using TonWeb
async function buildJettonTransferPayload(amount, destination, responseDestination) {
    console.log('Building Jetton transfer payload:', { amount, destination, responseDestination });
    
    try {
        // Check if TonWeb is available
        if (typeof TonWeb === 'undefined') {
            throw new Error('TonWeb not loaded');
        }
        
        const tonweb = new TonWeb();
        const Cell = TonWeb.boc.Cell;
        const Address = TonWeb.utils.Address;
        
        // Parse addresses
        const destAddress = new Address(destination);
        const respAddress = new Address(responseDestination);
        
        // Build Jetton transfer body cell
        // transfer#0f8a7ea5 query_id:uint64 amount:(VarUInteger 16) destination:MsgAddress
        //                   response_destination:MsgAddress custom_payload:(Maybe ^Cell)
        //                   forward_ton_amount:(VarUInteger 16) forward_payload:(Either Cell ^Cell)
        
        const cell = new Cell();
        cell.bits.writeUint(0x0f8a7ea5, 32);  // Jetton transfer opcode
        cell.bits.writeUint(0, 64);            // query_id
        cell.bits.writeCoins(amount);          // amount (in Jetton units)
        cell.bits.writeAddress(destAddress);   // destination
        cell.bits.writeAddress(respAddress);   // response_destination
        cell.bits.writeBit(false);             // no custom_payload
        cell.bits.writeCoins(0);               // forward_ton_amount = 0
        cell.bits.writeBit(false);             // no forward_payload (inline)
        
        // Convert to BOC base64
        const boc = await cell.toBoc();
        const payload = TonWeb.utils.bytesToBase64(boc);
        
        console.log('Built Jetton payload:', payload);
        return payload;
        
    } catch (error) {
        console.error('Failed to build Jetton payload:', error);
        throw new Error('Failed to build Jetton transfer: ' + error.message);
    }
}

// Claim USDT deposit via API
async function claimUsdtDeposit(amountMicro) {
    const fromAddress = currentWalletAddress || null;
    
    const response = await fetch('/api/deposit/claim', {
        method: 'POST',
        headers: {
            'Content-Type': 'application/json',
            'X-Tg-UserId': telegramUserId.toString()
        },
        body: JSON.stringify({ 
            amountNano: amountMicro, // For USDT, this is micro (6 decimals)
            fromAddress,
            asset: 'USDT'
        })
    });
    
    return response.json();
}

// Claim deposit via API (includes fromAddress for verification)
async function claimDeposit(amountNano) {
    // Get wallet address for source verification
    const fromAddress = currentWalletAddress || null;
    
    const response = await fetch('/api/deposit/claim', {
        method: 'POST',
        headers: {
            'Content-Type': 'application/json',
            'X-Tg-UserId': telegramUserId.toString()
        },
        body: JSON.stringify({ 
            amountNano,
            fromAddress 
        })
    });
    
    return response.json();
}

// Verify deposit via API (actively checks blockchain)
async function verifyDeposit(depositId) {
    const response = await fetch('/api/deposit/verify', {
        method: 'POST',
        headers: {
            'Content-Type': 'application/json',
            'X-Tg-UserId': telegramUserId.toString()
        },
        body: JSON.stringify({ depositId })
    });
    
    return response.json();
}

// Poll deposit verification until confirmed or timeout
async function pollDepositStatus(depositId, maxAttempts = 30, intervalMs = 2000) {
    for (let i = 0; i < maxAttempts; i++) {
        try {
            // Use verify endpoint which actively checks blockchain
            const result = await verifyDeposit(depositId);
            console.log(`Verify ${i + 1}/${maxAttempts}: ${result.status}`);
            
            if (result.status === 'CONFIRMED') {
                return true;
            }
            
            if (result.status === 'REJECTED') {
                alert('Deposit was rejected');
                return false;
            }
            
            await new Promise(resolve => setTimeout(resolve, intervalMs));
        } catch (error) {
            console.error('Verify error:', error);
        }
    }
    
    return false;
}

// === Withdraw ===
function selectWithdrawAsset(asset) {
    state.withdrawAsset = asset;
    
    document.querySelectorAll('.withdraw__asset-btn').forEach(btn => {
        btn.classList.toggle('withdraw__asset-btn--active', btn.dataset.asset === asset);
    });
}

async function handleWithdraw() {
    const amount = document.getElementById('withdraw-amount').value;
    const toAddress = document.getElementById('withdraw-address').value;
    
    if (!amount || parseFloat(amount) <= 0) {
        alert('Please enter a valid amount');
        return;
    }
    
    if (!toAddress) {
        alert('Please enter a destination address');
        return;
    }
    
    try {
        await apiCall('/api/withdraw', {
            method: 'POST',
            body: JSON.stringify({
                asset: state.withdrawAsset,
                amount: state.withdrawAsset === 'TON' 
                    ? Math.floor(parseFloat(amount) * 1_000_000_000)
                    : Math.floor(parseFloat(amount) * 1_000_000),
                toAddress: toAddress
            })
        });
        
        alert('Withdraw request created!');
        document.getElementById('withdraw-amount').value = '';
        document.getElementById('withdraw-address').value = '';
        await loadState();
        
    } catch (error) {
        alert(error.message);
    }
}
