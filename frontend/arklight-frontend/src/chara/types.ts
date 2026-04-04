export interface CharacterEmotionReference {
  emotion: string;
  fileName: string;
}

export interface CharacterProfile {
  name: string;
  defaultEmotion: string;
  emotions: CharacterEmotionReference[];
  iconUrl: string;
  imageUrls: Record<string, string>;
}

export interface ParsedEmotionMessage {
  emotion: string | null;
  content: string;
}
