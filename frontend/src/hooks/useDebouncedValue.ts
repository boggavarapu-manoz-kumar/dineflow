import { useEffect, useState } from 'react';

/**
 * Debounces rapidly changing input values (e.g. menu or bill search) to prevent
 * unnecessary filtering and re-renders on every keystroke.
 */
export function useDebouncedValue<T>(value: T, delayMs = 250): T {
  const [debounced, setDebounced] = useState<T>(value);

  useEffect(() => {
    const timer = setTimeout(() => setDebounced(value), delayMs);
    return () => clearTimeout(timer);
  }, [value, delayMs]);

  return debounced;
}
