const INSTALLED_CHARACTERS_STORAGE_KEY = 'arklight.characters.installed';

export const loadInstalledCharacterNames = () => {
  if (typeof window === 'undefined') {
    return [] as string[];
  }

  try {
    const rawValue = window.localStorage.getItem(INSTALLED_CHARACTERS_STORAGE_KEY);
    if (!rawValue) {
      return [] as string[];
    }

    const parsedValue = JSON.parse(rawValue);
    return Array.isArray(parsedValue)
      ? parsedValue.filter((item): item is string => typeof item === 'string')
      : [];
  } catch {
    return [] as string[];
  }
};

export const saveInstalledCharacterNames = (characterNames: string[]) => {
  if (typeof window === 'undefined') {
    return;
  }

  window.localStorage.setItem(
    INSTALLED_CHARACTERS_STORAGE_KEY,
    JSON.stringify(Array.from(new Set(characterNames)).sort((left, right) => left.localeCompare(right, 'zh-CN')))
  );
};
