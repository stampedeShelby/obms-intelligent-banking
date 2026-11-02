/// <reference types="vite/client" />

/**
 * Typed environment variables.
 *
 * Only variables prefixed `VITE_` are exposed to the browser bundle by Vite.
 * Nothing secret belongs here: anything in this interface ships to the client.
 */
interface ImportMetaEnv {
  /** Base URL of the OBMS backend. Empty in development, where Vite proxies /api. */
  readonly VITE_API_BASE_URL?: string;
}

interface ImportMeta {
  readonly env: ImportMetaEnv;
}
