import { normalizeEmotionToken } from './message-parser';
import type { CharacterEmotionReference, CharacterProfile } from './types';

type EmotionJsonValue =
  | string[]
  | Array<Record<string, unknown>>
  | Record<string, unknown>;

const emotionJsonModules = import.meta.glob('@project-chara/*/emotion/emotions.json', {
  eager: true,
  import: 'default'
}) as Record<string, EmotionJsonValue>;

const emotionImageModules = import.meta.glob('@project-chara/*/emotion/*.{png,jpg,jpeg,webp,gif}', {
  eager: true,
  query: '?url',
  import: 'default'
}) as Record<string, string>;

const NUMERIC_KEY_PATTERN = /^\d+$/;

const EMOTION_FILE_ALIASES: Record<string, string[]> = {
  哭泣: ['伤心'],
  伤心: ['哭泣'],
  难为情: ['羞耻'],
  羞耻: ['难为情'],
  平静: ['正常'],
  普通: ['正常'],
  默认: ['正常', '头像'],
  开心: ['高兴']
};

const normalizeFileBaseName = (fileName: string) => fileName.replace(/\.[^.]+$/, '');

const parseCharacterAssetPath = (rawPath: string) => {
  const normalizedPath = rawPath.replace(/\\/g, '/');
  const pathSegments = normalizedPath.split('/').filter(Boolean);
  const charaIndex = pathSegments.lastIndexOf('chara');

  if (charaIndex === -1 || pathSegments[charaIndex + 2] !== 'emotion') {
    return null;
  }

  const characterName = pathSegments[charaIndex + 1];
  const filePath = pathSegments.slice(charaIndex + 3).join('/');

  if (!characterName || !filePath) {
    return null;
  }

  return {
    characterName,
    filePath
  };
};

const resolveImageFileName = (
  imageMap: Record<string, string>,
  emotionName: string,
  preferredFileName: string
) => {
  const candidates = [
    preferredFileName,
    emotionName,
    ...(EMOTION_FILE_ALIASES[emotionName] ?? [])
  ].map((candidate) => normalizeFileBaseName(candidate));

  const normalizedImageEntries = Object.keys(imageMap).map((fileName) => ({
    fileName,
    normalized: normalizeEmotionToken(fileName)
  }));

  for (const candidate of candidates) {
    if (imageMap[candidate]) {
      return candidate;
    }

    const normalizedCandidate = normalizeEmotionToken(candidate);
    const matchedEntry = normalizedImageEntries.find((entry) => entry.normalized === normalizedCandidate);
    if (matchedEntry) {
      return matchedEntry.fileName;
    }
  }

  return '';
};

const getFirstImageFileName = (imageMap: Record<string, string>) =>
  Object.keys(imageMap)[0] ?? '';

const getEmotionAtIndex = (
  rawValue: EmotionJsonValue,
  index: number
): CharacterEmotionReference | null => {
  if (Array.isArray(rawValue)) {
    const item = rawValue[index];
    if (typeof item === 'string') {
      return {
        emotion: item.trim(),
        fileName: item.trim()
      };
    }

    if (!item || typeof item !== 'object') {
      return null;
    }

    const objectItem = item as Record<string, unknown>;
    const emotionName = [objectItem.name, objectItem.label, objectItem.key]
      .find((value) => typeof value === 'string');
    const imageName = [objectItem.file, objectItem.fileName, objectItem.image, objectItem.path]
      .find((value) => typeof value === 'string');

    if (typeof emotionName !== 'string') {
      return null;
    }

    return {
      emotion: emotionName.trim(),
      fileName: (typeof imageName === 'string' ? imageName : emotionName).trim()
    };
  }

  if (!rawValue || typeof rawValue !== 'object') {
    return null;
  }

  const objectValue = rawValue as Record<string, unknown>;
  if (Array.isArray(objectValue.emotions)) {
    return getEmotionAtIndex(objectValue.emotions as EmotionJsonValue, index);
  }

  const indexedValue = objectValue[String(index)];
  if (typeof indexedValue === 'string') {
    return {
      emotion: indexedValue.trim(),
      fileName: indexedValue.trim()
    };
  }

  if (!indexedValue || typeof indexedValue !== 'object') {
    return null;
  }

  const indexedObject = indexedValue as Record<string, unknown>;
  const emotionName = [indexedObject.name, indexedObject.label, indexedObject.key]
    .find((value) => typeof value === 'string');
  const imageName = [indexedObject.file, indexedObject.fileName, indexedObject.image, indexedObject.path]
    .find((value) => typeof value === 'string');

  if (typeof emotionName !== 'string') {
    return null;
  }

  return {
    emotion: emotionName.trim(),
    fileName: (typeof imageName === 'string' ? imageName : emotionName).trim()
  };
};

const parseEmotionList = (rawValue: EmotionJsonValue): CharacterEmotionReference[] => {
  const buildEntry = (emotion: string, fileName?: string) => ({
    emotion: emotion.trim(),
    fileName: (fileName ?? emotion).trim()
  });

  if (Array.isArray(rawValue)) {
    return rawValue
      .map((item) => {
        if (typeof item === 'string') {
          return buildEntry(item);
        }

        if (!item || typeof item !== 'object') {
          return null;
        }

        const objectItem = item as Record<string, unknown>;
        const emotionName = [objectItem.name, objectItem.label, objectItem.key]
          .find((value) => typeof value === 'string');
        const imageName = [objectItem.file, objectItem.fileName, objectItem.image, objectItem.path]
          .find((value) => typeof value === 'string');

        if (typeof emotionName !== 'string') {
          return null;
        }

        return buildEntry(emotionName, typeof imageName === 'string' ? imageName : emotionName);
      })
      .filter((item): item is CharacterEmotionReference => Boolean(item));
  }

  if (!rawValue || typeof rawValue !== 'object') {
    return [];
  }

  const objectValue = rawValue as Record<string, unknown>;

  if (Array.isArray(objectValue.emotions)) {
    return parseEmotionList(objectValue.emotions as EmotionJsonValue);
  }

  return Object.entries(objectValue)
    .map(([emotionName, value]) => {
      if (typeof value === 'string') {
        if (NUMERIC_KEY_PATTERN.test(emotionName)) {
          return buildEntry(value, value);
        }

        return buildEntry(emotionName, value);
      }

      if (!value || typeof value !== 'object') {
        return null;
      }

      const objectItem = value as Record<string, unknown>;
      const configuredEmotionName = [objectItem.name, objectItem.label, objectItem.key]
        .find((candidate) => typeof candidate === 'string');
      const imageName = [objectItem.file, objectItem.fileName, objectItem.image, objectItem.path]
        .find((candidate) => typeof candidate === 'string');

      return buildEntry(
        typeof configuredEmotionName === 'string'
          ? configuredEmotionName
          : NUMERIC_KEY_PATTERN.test(emotionName) && typeof imageName === 'string'
            ? imageName
            : emotionName,
        typeof imageName === 'string'
          ? imageName
          : typeof configuredEmotionName === 'string'
            ? configuredEmotionName
            : emotionName
      );
    })
    .filter((item): item is CharacterEmotionReference => Boolean(item));
};

const buildCharacterCatalog = () => {
  const characterImages = Object.entries(emotionImageModules).reduce<Record<string, Record<string, string>>>(
    (accumulator, [rawPath, assetUrl]) => {
      const parsedPath = parseCharacterAssetPath(rawPath);
      if (!parsedPath) {
        return accumulator;
      }

      const fileName = parsedPath.filePath.split('/').pop() ?? parsedPath.filePath;
      const imageKey = normalizeFileBaseName(fileName);

      accumulator[parsedPath.characterName] ??= {};
      accumulator[parsedPath.characterName][imageKey] = assetUrl;
      return accumulator;
    },
    {}
  );

  return Object.entries(emotionJsonModules)
    .map(([rawPath, rawValue]) => {
      const parsedPath = parseCharacterAssetPath(rawPath);
      if (!parsedPath) {
        return null;
      }

      const characterName = parsedPath.characterName;
      const imageMap = characterImages[characterName] ?? {};
      const parsedEmotions = parseEmotionList(rawValue);
      const configuredEmotions = parsedEmotions
        .map((emotion) => ({
          emotion: emotion.emotion,
          fileName: resolveImageFileName(
            imageMap,
            emotion.emotion,
            normalizeFileBaseName(emotion.fileName)
          )
        }))
        .filter((emotion) => imageMap[emotion.fileName]);

      const fallbackEmotions = Object.keys(imageMap).map((emotionName) => ({
        emotion: emotionName,
        fileName: emotionName
      }));

      const emotions = configuredEmotions.length ? configuredEmotions : fallbackEmotions;
      if (!emotions.length) {
        return null;
      }

      const imageUrls = emotions.reduce<Record<string, string>>((accumulator, emotion) => {
        accumulator[emotion.emotion] = imageMap[emotion.fileName];
        return accumulator;
      }, {});

      Object.entries(imageMap).forEach(([fileName, assetUrl]) => {
        if (!imageUrls[fileName]) {
          imageUrls[fileName] = assetUrl;
        }
      });

      const primaryEmotion = getEmotionAtIndex(rawValue, 0) ?? parsedEmotions[0] ?? null;
      const defaultEmotion = primaryEmotion?.emotion ?? emotions[0].emotion;
      const defaultFileName = primaryEmotion
        ? resolveImageFileName(
            imageMap,
            primaryEmotion.emotion,
            normalizeFileBaseName(primaryEmotion.fileName)
          ) || emotions[0]?.fileName || getFirstImageFileName(imageMap)
        : emotions[0]?.fileName || getFirstImageFileName(imageMap);

      if (defaultEmotion && defaultFileName && imageMap[defaultFileName]) {
        imageUrls[defaultEmotion] = imageMap[defaultFileName];
      }

      return {
        name: characterName,
        defaultEmotion,
        emotions: parsedEmotions.length ? parsedEmotions : emotions,
        iconUrl: imageUrls[defaultEmotion] ?? '',
        imageUrls
      } satisfies CharacterProfile;
    })
    .filter((item): item is CharacterProfile => Boolean(item))
    .sort((left, right) => left.name.localeCompare(right.name, 'zh-CN'));
};

const characterCatalog = buildCharacterCatalog();

export const loadCharacterCatalog = async () => characterCatalog;
