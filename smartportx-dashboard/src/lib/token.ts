export interface TokenPair {
  accessToken: string;
  refreshToken: string;
  expiresIn: number;
  tokenType: string;
}

let current: TokenPair | null = null;

export const tokenStore = {
  get(): TokenPair | null {
    return current;
  },
  set(pair: TokenPair): void {
    current = pair;
  },
  clear(): void {
    current = null;
  },
  has(): boolean {
    return current !== null;
  },
};

export function accessToken(): string | null {
  return current?.accessToken ?? null;
}