import { createContext } from 'react';

export type Theme = 'light' | 'dark';

export interface ThemeState {
  theme: Theme;
  toggle: () => void;
}

/** The theme context object, separate from the provider and the hook. */
export const ThemeContext = createContext<ThemeState | null>(null);

/** Where the chosen theme is remembered. */
export const THEME_STORAGE_KEY = 'obms.theme';
