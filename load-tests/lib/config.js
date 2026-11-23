import { fail } from 'k6';

/**
 * Safety guard: prevents accidental load-test execution against production environments.
 */
export function assertStagingSafety(baseUrl) {
  if (__ENV.ALLOW_LOAD_TEST !== 'true') {
    fail('SAFETY GUARD: Set -e ALLOW_LOAD_TEST=true to confirm authorized isolated staging execution.');
  }
  const lower = (baseUrl || '').toLowerCase();
  if (lower.includes('prod.') || lower.includes('production') || lower.includes('api.dineflow.com')) {
    fail(`SAFETY GUARD: Refusing to run load test against production-like URL: ${baseUrl}`);
  }
}

/**
 * Repeatable load profiles clearly distinguishing VUs, duration, and ramp shapes.
 */
export function buildStagesForProfile(profileName) {
  switch ((profileName || 'baseline').toLowerCase()) {
    case 'baseline':
      return [
        { duration: '15s', target: 5 },
        { duration: '45s', target: 5 },
        { duration: '15s', target: 0 },
      ];
    case 'normal':
      return [
        { duration: '1m', target: 25 },
        { duration: '5m', target: 25 },
        { duration: '1m', target: 0 },
      ];
    case 'peak':
      return [
        { duration: '2m', target: 50 },
        { duration: '2m', target: 100 },
        { duration: '5m', target: 100 },
        { duration: '1m', target: 0 },
      ];
    case 'burst':
      return [
        { duration: '20s', target: 10 },
        { duration: '45s', target: 150 },
        { duration: '30s', target: 10 },
        { duration: '15s', target: 0 },
      ];
    case 'sustained':
      return [
        { duration: '2m', target: 60 },
        { duration: '30m', target: 60 },
        { duration: '2m', target: 0 },
      ];
    case 'recovery':
      return [
        { duration: '1m', target: 40 },
        { duration: '8m', target: 40 },
        { duration: '1m', target: 0 },
      ];
    default:
      fail(`Unsupported PROFILE=${profileName}. Expected: baseline|normal|peak|burst|sustained|recovery`);
  }
}
