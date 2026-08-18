import { useCallback, useEffect, useState } from 'react';

export interface AsyncData<T> {
  data: T | null;
  loading: boolean;
  error: unknown;
  /** Re-runs the loader; what the retry banners call. */
  reload: () => void;
}

/**
 * The loading/empty/error triad every route in PRD §3.8 needs, in one place.
 *
 * The `cancelled` flag matters more than it looks: without it, a fast route change lands
 * a resolved response on an unmounted page, and under React 18 StrictMode every effect
 * runs twice in development, so the second response would clobber the first.
 */
export function useAsyncData<T>(loader: () => Promise<T>, deps: readonly unknown[]): AsyncData<T> {
  const [data, setData] = useState<T | null>(null);
  const [loading, setLoading] = useState(true);
  const [error, setError] = useState<unknown>(null);
  const [reloadToken, setReloadToken] = useState(0);

  const reload = useCallback(() => {
    setReloadToken((token) => token + 1);
  }, []);

  useEffect(() => {
    let cancelled = false;
    setLoading(true);
    setError(null);

    loader()
      .then((result) => {
        if (!cancelled) {
          setData(result);
        }
      })
      .catch((caught: unknown) => {
        if (!cancelled) {
          setData(null);
          setError(caught);
        }
      })
      .finally(() => {
        if (!cancelled) {
          setLoading(false);
        }
      });

    return () => {
      cancelled = true;
    };
    // The loader closes over the caller's own dependencies, which they pass explicitly.
    // eslint-disable-next-line react-hooks/exhaustive-deps
  }, [...deps, reloadToken]);

  return { data, loading, error, reload };
}
