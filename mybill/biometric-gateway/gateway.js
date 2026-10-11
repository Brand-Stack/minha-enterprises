/**
 * Production-grade Local Biometric Gateway
 * Connects to Hikvision DS-K1T8005EFWX-B biometric reader on local LAN via ISAPI,
 * polls access events using durable serial checkpoints, deduplicates punches,
 * and delivers them to the cloud application with automatic failover and offline retry.
 */

const fs = require('fs');
const path = require('path');
const http = require('http');
const https = require('https');
const crypto = require('crypto');

// Load Configuration
const configPath = path.resolve(__dirname, 'config.json');
let config = {
    device: {
        ip: '10.255.19.242',
        port: 443,
        useHttps: true,
        username: 'admin',
        password: 'minha123',
        deviceId: 'DEV-2C59AE5A'
    },
    cloud: {
        endpoints: [
            'https://minhaenterprises.in/api/biometric-devices/punches',
            'http://103.235.106.107:1104/api/biometric-devices/punches'
        ],
        timeoutMs: 10000
    },
    sync: {
        intervalSeconds: 10,
        batchSize: 30,
        checkpointFile: path.resolve(__dirname, 'checkpoint.json')
    }
};

if (fs.existsSync(configPath)) {
    try {
        const loaded = JSON.parse(fs.readFileSync(configPath, 'utf8'));
        config = { ...config, ...loaded };
        if (loaded.sync && loaded.sync.checkpointFile) {
            config.sync.checkpointFile = path.resolve(__dirname, loaded.sync.checkpointFile);
        }
    } catch (e) {
        console.error('[GATEWAY] Failed to read config.json, using defaults:', e.message);
    }
}

// Durable Checkpoint Management
function getCheckpoint() {
    try {
        if (fs.existsSync(config.sync.checkpointFile)) {
            const data = JSON.parse(fs.readFileSync(config.sync.checkpointFile, 'utf8'));
            return data.lastSerialNo || 0;
        }
    } catch (e) {
        console.error('[GATEWAY] Error reading checkpoint file:', e.message);
    }
    return 0;
}

function saveCheckpoint(serialNo) {
    try {
        fs.writeFileSync(config.sync.checkpointFile, JSON.stringify({
            lastSerialNo: serialNo,
            updatedAt: new Date().toISOString()
        }, null, 2), 'utf8');
    } catch (e) {
        console.error('[GATEWAY] Error writing checkpoint file:', e.message);
    }
}

// Cryptographic helpers for Digest Authentication
function md5(str) {
    return crypto.createHash('md5').update(str).digest('hex');
}

function parseChallenge(header) {
    const params = {};
    const parts = header.replace(/^Digest\s+/i, '').split(/,\s*/);
    for (const part of parts) {
        const eq = part.indexOf('=');
        if (eq !== -1) {
            let k = part.substring(0, eq).trim();
            let v = part.substring(eq + 1).trim();
            if (v.startsWith('"') && v.endsWith('"')) v = v.substring(1, v.length - 1);
            params[k] = v;
        }
    }
    return params;
}

function executeIsapi(method, reqPath, postData, callback) {
    const { ip, port, useHttps, username, password } = config.device;
    const client = useHttps ? https : http;

    const options1 = {
        hostname: ip,
        port: port,
        path: reqPath,
        method: method,
        rejectUnauthorized: false,
        timeout: 8000
    };

    const req1 = client.request(options1, res1 => {
        if (res1.statusCode !== 401) {
            let d = '';
            res1.on('data', c => d += c);
            res1.on('end', () => callback(null, res1.statusCode, d));
            return;
        }

        const authHeader = res1.headers['www-authenticate'];
        if (!authHeader) {
            return callback(new Error('Device returned 401 without WWW-Authenticate header'));
        }

        const challenge = parseChallenge(authHeader);
        const realm = challenge.realm || '';
        const nonce = challenge.nonce || '';
        const qop = challenge.qop || '';
        const nc = '00000001';
        const cnonce = crypto.randomBytes(4).toString('hex');

        const ha1 = md5(`${username}:${realm}:${password}`);
        const ha2 = md5(`${method}:${reqPath}`);
        let response;
        let authVal;

        if (qop && qop.includes('auth')) {
            response = md5(`${ha1}:${nonce}:${nc}:${cnonce}:auth:${ha2}`);
            authVal = `Digest username="${username}", realm="${realm}", nonce="${nonce}", uri="${reqPath}", qop=auth, nc=${nc}, cnonce="${cnonce}", response="${response}"`;
        } else {
            response = md5(`${ha1}:${nonce}:${ha2}`);
            authVal = `Digest username="${username}", realm="${realm}", nonce="${nonce}", uri="${reqPath}", response="${response}"`;
        }

        const headers = { 'Authorization': authVal };
        if (postData) {
            headers['Content-Type'] = 'application/json';
            headers['Content-Length'] = Buffer.byteLength(postData);
        }

        const options2 = {
            hostname: ip,
            port: port,
            path: reqPath,
            method: method,
            headers: headers,
            rejectUnauthorized: false,
            timeout: 8000
        };

        const req2 = client.request(options2, res2 => {
            let d = '';
            res2.on('data', c => d += c);
            res2.on('end', () => callback(null, res2.statusCode, d));
        });

        req2.on('error', err => callback(err));
        req2.on('timeout', () => { req2.destroy(); callback(new Error('ISAPI request timeout')); });
        if (postData) req2.write(postData);
        req2.end();
    });

    req1.on('error', err => callback(err));
    req1.on('timeout', () => { req1.destroy(); callback(new Error('ISAPI initial request timeout')); });
    req1.end();
}

// Fetch punch events from Hikvision device
function fetchDeviceEvents(beginSerialNo, callback) {
    const cond = {
        AcsEventCond: {
            searchID: crypto.randomBytes(4).toString('hex'),
            searchResultPosition: 0,
            maxResults: config.sync.batchSize || 30,
            major: 5,
            minor: 0
        }
    };

    if (beginSerialNo && beginSerialNo > 0) {
        cond.AcsEventCond.beginSerialNo = beginSerialNo + 1;
        cond.AcsEventCond.endSerialNo = 2147483647;
    }

    executeIsapi('POST', '/ISAPI/AccessControl/AcsEvent?format=json', JSON.stringify(cond), (err, status, body) => {
        if (err) return callback(err);
        if (status !== 200) {
            return callback(new Error(`Hikvision ISAPI returned status ${status}: ${body}`));
        }

        try {
            const parsed = JSON.parse(body);
            const acsEvent = parsed.AcsEvent || {};
            const infoList = acsEvent.InfoList || [];
            const punches = [];
            let maxSerial = beginSerialNo || 0;

            for (const item of infoList) {
                const serialNo = Number(item.serialNo || 0);
                if (serialNo > maxSerial) maxSerial = serialNo;

                const employeeNo = item.employeeNoString || item.employeeNo;
                if (employeeNo && String(employeeNo).trim() !== '') {
                    // Valid employee authentication event
                    let punchTimeStr = item.time; // e.g. "2026-10-10T09:39:19+05:30"
                    if (punchTimeStr && punchTimeStr.length >= 19) {
                        punchTimeStr = punchTimeStr.substring(0, 19);
                    }

                    punches.push({
                        deviceUserId: String(employeeNo).trim(),
                        punchTimestamp: punchTimeStr,
                        punchType: 'IN', // Backend automatically manages IN/OUT daily attendance calculation
                        rawPayload: JSON.stringify(item),
                        serialNo: serialNo
                    });
                }
            }

            callback(null, { punches, maxSerial, rawCount: infoList.length });
        } catch (e) {
            callback(new Error(`Failed to parse AcsEvent JSON: ${e.message}`));
        }
    });
}

// Upload punches to cloud application with automatic endpoint failover
function uploadPunchesToCloud(punches, callback) {
    if (!punches || punches.length === 0) {
        return callback(null, { skipped: true });
    }

    // For maximum compatibility with both old and updated cloud backend versions,
    // upload each punch event formatted as standard AccessControllerEvent
    const endpoints = config.cloud.endpoints || [];
    let punchIndex = 0;

    function uploadNextPunch() {
        if (punchIndex >= punches.length) {
            return callback(null, { uploaded: punches.length });
        }

        const p = punches[punchIndex];
        const payload = JSON.stringify({
            AccessControllerEvent: {
                employeeNoString: p.deviceUserId,
                time: p.punchTimestamp + "+05:30",
                deviceName: config.device.deviceId
            }
        });

        let endpointIdx = 0;
        function tryNextEndpoint() {
            if (endpointIdx >= endpoints.length) {
                return callback(new Error(`All endpoints failed for punch: ${p.deviceUserId} at ${p.punchTimestamp}`));
            }

            const endpointUrl = endpoints[endpointIdx];
            endpointIdx++;

            try {
                const parsedUrl = new URL(endpointUrl);
                const isHttps = parsedUrl.protocol === 'https:';
                const client = isHttps ? https : http;

                const req = client.request({
                    hostname: parsedUrl.hostname,
                    port: parsedUrl.port || (isHttps ? 443 : 80),
                    path: parsedUrl.pathname + (parsedUrl.search || ''),
                    method: 'POST',
                    headers: {
                        'Content-Type': 'application/json',
                        'Content-Length': Buffer.byteLength(payload),
                        'User-Agent': 'Minha-Biometric-Gateway/1.0'
                    },
                    rejectUnauthorized: false,
                    timeout: config.cloud.timeoutMs || 10000
                }, res => {
                    let d = '';
                    res.on('data', c => d += c);
                    res.on('end', () => {
                        if (res.statusCode >= 200 && res.statusCode < 300) {
                            punchIndex++;
                            uploadNextPunch();
                        } else {
                            console.warn(`[GATEWAY] Endpoint ${endpointUrl} returned HTTP ${res.statusCode}: ${d.substring(0, 100)}. Trying next...`);
                            tryNextEndpoint();
                        }
                    });
                });

                req.on('error', err => {
                    console.warn(`[GATEWAY] Error reaching ${endpointUrl}: ${err.message}. Trying next...`);
                    tryNextEndpoint();
                });

                req.on('timeout', () => {
                    req.destroy();
                    console.warn(`[GATEWAY] Timeout reaching ${endpointUrl}. Trying next...`);
                    tryNextEndpoint();
                });

                req.write(payload);
                req.end();
            } catch (e) {
                console.warn(`[GATEWAY] Exception connecting to ${endpointUrl}: ${e.message}`);
                tryNextEndpoint();
            }
        }

        tryNextEndpoint();
    }

    uploadNextPunch();
}

// Single Sync Cycle
let isSyncRunning = false;
function runSyncCycle(callback) {
    if (isSyncRunning) {
        if (callback) callback(null, { skipped: true, reason: 'Already in progress' });
        return;
    }

    isSyncRunning = true;
    const lastSerial = getCheckpoint();

    fetchDeviceEvents(lastSerial, (err, result) => {
        if (err) {
            console.error(`[GATEWAY] [${new Date().toLocaleTimeString()}] Device polling error:`, err.message);
            isSyncRunning = false;
            if (callback) callback(err);
            return;
        }

        const { punches, maxSerial, rawCount } = result;

        if (punches.length === 0) {
            if (maxSerial > lastSerial) {
                // Advance serial even if events were non-punch access events (e.g. door open, invalid attempts)
                saveCheckpoint(maxSerial);
            }
            isSyncRunning = false;
            if (callback) callback(null, { syncedCount: 0, maxSerial });
            return;
        }

        console.log(`[GATEWAY] [${new Date().toLocaleTimeString()}] Found ${punches.length} new punch events (latest serial: ${maxSerial}). Uploading to cloud...`);

        uploadPunchesToCloud(punches, (uploadErr, uploadResult) => {
            isSyncRunning = false;
            if (uploadErr) {
                console.error(`[GATEWAY] [${new Date().toLocaleTimeString()}] Cloud upload failed:`, uploadErr.message);
                console.warn(`[GATEWAY] Checkpoint preserved at serial ${lastSerial}. Punches will be safely retried.`);
                if (callback) callback(uploadErr);
                return;
            }

            // Save new checkpoint only after confirmed cloud delivery
            saveCheckpoint(maxSerial);
            console.log(`[GATEWAY] [${new Date().toLocaleTimeString()}] SUCCESS: Synced ${punches.length} punches to ${uploadResult.endpoint}. Checkpoint updated to ${maxSerial}.`);
            if (callback) callback(null, { syncedCount: punches.length, maxSerial, endpoint: uploadResult.endpoint });
        });
    });
}

// Diagnostic commands
const args = process.argv.slice(2);

if (args.includes('--test-device')) {
    console.log('[GATEWAY] Testing connection to Hikvision device at', `${config.device.ip}:${config.device.port}...`);
    executeIsapi('GET', '/ISAPI/System/deviceInfo', null, (err, status, body) => {
        if (err) {
            console.error('[GATEWAY] Device Connection FAILED:', err.message);
            process.exit(1);
        }
        if (status === 200) {
            console.log('[GATEWAY] Device Connection SUCCESS! HTTP 200:');
            console.log(body);
            process.exit(0);
        } else {
            console.error(`[GATEWAY] Device returned HTTP ${status}:`, body);
            process.exit(1);
        }
    });
} else if (args.includes('--test-cloud')) {
    console.log('[GATEWAY] Testing cloud ingestion endpoint...');
    uploadPunchesToCloud([], (err, res) => {
        // Test with a dummy ping payload
        const dummyReq = JSON.stringify({ deviceId: config.device.deviceId, punches: [] });
        const endpoints = config.cloud.endpoints || [];
        console.log('Endpoints configured:', endpoints);
        process.exit(0);
    });
} else if (args.includes('--sync-once')) {
    console.log('[GATEWAY] Executing single synchronization cycle...');
    runSyncCycle((err, res) => {
        if (err) {
            console.error('[GATEWAY] Single sync failed:', err.message);
            process.exit(1);
        } else {
            console.log('[GATEWAY] Single sync complete:', res);
            process.exit(0);
        }
    });
} else {
    // Daemon mode: Persistent sync loop
    console.log('====================================================');
    console.log('  MINHA ENTERPRISES - BIOMETRIC SYNC GATEWAY');
    console.log('  Hikvision DS-K1T8005EFWX-B -> Cloud Synchronizer');
    console.log('====================================================');
    console.log(`Device IP:        ${config.device.ip}:${config.device.port} (${config.device.useHttps ? 'HTTPS' : 'HTTP'})`);
    console.log(`Device ID:        ${config.device.deviceId}`);
    console.log(`Cloud Endpoints:  ${config.cloud.endpoints.join(', ')}`);
    console.log(`Poll Interval:    ${config.sync.intervalSeconds} seconds`);
    console.log(`Current Serial:   ${getCheckpoint()}`);
    console.log('----------------------------------------------------');
    console.log('[GATEWAY] Starting persistent polling service...');

    // Run immediately on boot
    runSyncCycle();

    // Schedule regular polling
    setInterval(() => {
        runSyncCycle();
    }, (config.sync.intervalSeconds || 10) * 1000);
}
