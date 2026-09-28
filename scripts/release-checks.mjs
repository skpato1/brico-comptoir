// Return identifiers only: never include a matched credential in diagnostics.
export function sensitiveRules(text, knownSecrets = []) {
  const rules = [
    ['PRIVATE_KEY', /-----BEGIN (?:RSA |EC |OPENSSH |DSA |ENCRYPTED )?PRIVATE KEY-----/],
    ['GITHUB_TOKEN', /\b(?:gh[pousr]_[A-Za-z0-9]{36,}|github_pat_[A-Za-z0-9_]{50,})\b/],
    ['AWS_ACCESS_KEY', /\b(?:AKIA|ASIA)[A-Z0-9]{16}\b/],
    ['SLACK_TOKEN', /\bxox[baprs]-[A-Za-z0-9-]{20,}\b/],
    ['CREDENTIAL_URL', /\b(?:postgres(?:ql)?|mysql|mongodb(?:\+srv)?)\:\/\/[^\s/:]+:[^\s/@]+@/],
  ];
  const found = rules.filter(([, pattern]) => pattern.test(text)).map(([name]) => name);
  if (knownSecrets.some(secret => secret.length >= 12 && text.includes(secret))) found.push('LOCAL_SECRET_VALUE');
  return found;
}

export function privatePath(name) {
  return /(^|\/)\.env(?:$|\.)/.test(name) && !name.endsWith('.example')
    || /(^|\/)(?:\.local|\.vercel|node_modules|target|dist|coverage|test-results|playwright-report|backups|uploads|imports|secrets)(\/|$)/.test(name)
    || /\.(?:key|pem|p12|pfx|jks|keystore|dump|backup|vhdx|class|tar|tar\.gz|sql\.gz)$/i.test(name)
    || /(^|\/)(?:id_rsa|id_ed25519|credentials[^/]*\.json|secrets[^/]*\.json)$/.test(name);
}
