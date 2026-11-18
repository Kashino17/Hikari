export const dirname = (p) => (p.includes('/') ? p.slice(0, p.lastIndexOf('/')) || '/' : '.');
export const join = (...a) => a.join('/').replace(/\/+/g, '/');
export default { dirname, join };
