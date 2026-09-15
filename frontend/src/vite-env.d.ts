/// <reference types="vite/client" />

interface ImportMetaEnv {
  readonly VITE_API_BASE_URL: string;
  readonly VITE_PROTECTED_EXTENSIONS?: string;
}

interface ImportMeta {
  readonly env: ImportMetaEnv;
}
