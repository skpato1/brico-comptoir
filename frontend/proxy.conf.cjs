// Local dev proxy only. Browser requests always remain on the same origin.
const target = process.env.API_PROXY_TARGET || 'http://localhost:8080';
const parsed = new URL(target);
if (parsed.protocol !== 'http:' || !['localhost', '127.0.0.1', '[::1]'].includes(parsed.hostname.toLowerCase())
    || !parsed.port || parsed.username || parsed.password || parsed.pathname !== '/'
    || parsed.search || parsed.hash)
  throw new Error('API_PROXY_TARGET must be a loopback HTTP origin for local development');

module.exports = {
  '/api/**': {
    target,
    changeOrigin: false,
    secure: false,
  },
};
