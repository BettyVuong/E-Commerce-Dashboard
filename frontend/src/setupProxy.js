const { createProxyMiddleware } = require('http-proxy-middleware');

module.exports = function (app) {
    const backendProxy = process.env.BACKEND_PROXY || 'http://localhost:8080';

    app.use(createProxyMiddleware({
        target: backendProxy,
        changeOrigin: true,
        pathFilter: '/api'
    }));
};
