// src/types/CommunityChat.types.ts

export type ReportCategory =
  | 'SPAM'
  | 'HARASSMENT'
  | 'HATE_SPEECH'
  | 'VIOLENCE'
  | 'OTHER';

export interface AuthorDto {
  id: number;
  username: string;
  actualUsername?: string;
  profileImage: string | null;
  roleName?: string;
}

export interface SharedPostDto {
  id: number;
  content: string;
  authorUsername: string;
  mediaUrls?: string[];
  imageUrl?: string;
  createdAt?: string;
}

export type CommunityMessageType = 'TEXT' | 'SYSTEM' | 'SHARE_POST';

export interface ChatAttachmentDto {
  id?: number | string;
  url: string;
  attachmentType?: 'IMAGE' | 'FILE' | string;
  fileName?: string;
  fileSize?: number;
}

export interface ChatReactionDto {
  emoji: string;
  count: number;
  userReacted?: boolean;
}

export interface CommunityMessage {
  id?: number;
  messageId?: string;
  content?: string;
  sender: AuthorDto;
  messageType?: CommunityMessageType;
  replyToId?: number;
  sharedPost?: SharedPostDto;
  isEdited?: boolean;
  isPinned?: boolean;
  isFlagged?: boolean;
  createdAt: string;
  expiresAt?: string;
  clientSideId?: string; // Optional client-side correlation ID
  isDeleted?: boolean;
  deletedByType?: 'USER' | 'ADMINISTRATOR';
  attachments?: ChatAttachmentDto[];
  reactions?: ChatReactionDto[];
  status?: 'SENDING' | 'SENT' | 'FAILED';
}

export interface TypingIndicator {
  userId: number;
  username: string;
  typing: boolean;
}

export interface CommunityChatSettings {
  isGroupChatEnabled: boolean;
  chatRetentionDays: number;
}
