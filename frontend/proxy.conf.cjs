// Local dev proxy only. Browser requests always remain on the same origin.
module.exports = {
  '/api/**': {
    target: process.env.API_PROXY_TARGET || 'http://localhost:8080',
    changeOrigin: false,
    secure: false,
  },
};
