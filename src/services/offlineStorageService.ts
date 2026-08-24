import { Track } from '../types/music';

const DB_NAME = 'AuraSonic_Offline_DB';
const DB_VERSION = 1;
const STORE_MP4S = 'downloaded_mp4s';

export interface StoredMP4Record {
  videoId: string;
  track: Track;
  blob: Blob;
  size: number;
  downloadedAt: string;
}

class OfflineStorageService {
  private dbPromise: Promise<IDBDatabase> | null = null;
  private blobUrlCache: Map<string, string> = new Map();

  private getDB(): Promise<IDBDatabase> {
    if (this.dbPromise) return this.dbPromise;

    this.dbPromise = new Promise((resolve, reject) => {
      if (typeof window === 'undefined' || !window.indexedDB) {
        reject(new Error('IndexedDB not supported in this environment'));
        return;
      }

      const request = indexedDB.open(DB_NAME, DB_VERSION);

      request.onupgradeneeded = (event: IDBVersionChangeEvent) => {
        const db = (event.target as IDBOpenDBRequest).result;
        if (!db.objectStoreNames.contains(STORE_MP4S)) {
          db.createObjectStore(STORE_MP4S, { keyPath: 'videoId' });
        }
      };

      request.onsuccess = () => {
        resolve(request.result);
      };

      request.onerror = () => {
        reject(request.error);
      };
    });

    return this.dbPromise;
  }

  public async saveMP4(videoId: string, track: Track, blob: Blob): Promise<void> {
    const cleanId = videoId.replace('yt-', '');
    const db = await this.getDB();

    return new Promise((resolve, reject) => {
      const tx = db.transaction(STORE_MP4S, 'readwrite');
      const store = tx.objectStore(STORE_MP4S);

      const record: StoredMP4Record = {
        videoId: cleanId,
        track,
        blob,
        size: blob.size,
        downloadedAt: new Date().toLocaleString()
      };

      const request = store.put(record);

      request.onsuccess = () => {
        if (this.blobUrlCache.has(cleanId)) {
          URL.revokeObjectURL(this.blobUrlCache.get(cleanId)!);
        }
        const url = URL.createObjectURL(blob);
        this.blobUrlCache.set(cleanId, url);
        resolve();
      };

      request.onerror = () => reject(request.error);
    });
  }

  public async getMP4Blob(videoId: string): Promise<Blob | null> {
    const cleanId = videoId.replace('yt-', '');
    try {
      const db = await this.getDB();
      return new Promise((resolve) => {
        const tx = db.transaction(STORE_MP4S, 'readonly');
        const store = tx.objectStore(STORE_MP4S);
        const request = store.get(cleanId);

        request.onsuccess = () => {
          const res = request.result as StoredMP4Record | undefined;
          resolve(res ? res.blob : null);
        };

        request.onerror = () => resolve(null);
      });
    } catch {
      return null;
    }
  }

  public async getMP4PlaybackUrl(videoId: string): Promise<string | null> {
    const cleanId = videoId.replace('yt-', '');
    if (this.blobUrlCache.has(cleanId)) {
      return this.blobUrlCache.get(cleanId)!;
    }

    const blob = await this.getMP4Blob(cleanId);
    if (!blob) return null;

    const url = URL.createObjectURL(blob);
    this.blobUrlCache.set(cleanId, url);
    return url;
  }

  public async getAllDownloadedRecords(): Promise<StoredMP4Record[]> {
    try {
      const db = await this.getDB();
      return new Promise((resolve) => {
        const tx = db.transaction(STORE_MP4S, 'readonly');
        const store = tx.objectStore(STORE_MP4S);
        const request = store.getAll();

        request.onsuccess = () => {
          resolve((request.result as StoredMP4Record[]) || []);
        };

        request.onerror = () => resolve([]);
      });
    } catch {
      return [];
    }
  }

  public async hasMP4(videoId: string): Promise<boolean> {
    const cleanId = videoId.replace('yt-', '');
    if (this.blobUrlCache.has(cleanId)) return true;
    const blob = await this.getMP4Blob(cleanId);
    return blob !== null;
  }

  public async deleteMP4(videoId: string): Promise<void> {
    const cleanId = videoId.replace('yt-', '');
    if (this.blobUrlCache.has(cleanId)) {
      URL.revokeObjectURL(this.blobUrlCache.get(cleanId)!);
      this.blobUrlCache.delete(cleanId);
    }

    try {
      const db = await this.getDB();
      return new Promise((resolve) => {
        const tx = db.transaction(STORE_MP4S, 'readwrite');
        const store = tx.objectStore(STORE_MP4S);
        const request = store.delete(cleanId);
        request.onsuccess = () => resolve();
        request.onerror = () => resolve();
      });
    } catch {}
  }

  public async clearAll(): Promise<void> {
    for (const url of this.blobUrlCache.values()) {
      URL.revokeObjectURL(url);
    }
    this.blobUrlCache.clear();

    try {
      const db = await this.getDB();
      return new Promise((resolve) => {
        const tx = db.transaction(STORE_MP4S, 'readwrite');
        const store = tx.objectStore(STORE_MP4S);
        const request = store.clear();
        request.onsuccess = () => resolve();
        request.onerror = () => resolve();
      });
    } catch {}
  }
}

export const offlineStorage = new OfflineStorageService();
