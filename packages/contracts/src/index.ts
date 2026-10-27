export interface Project {
  id: string;
  name: string;
  location: string;
  areaSqFt: number | null;
  updatedAt: string;
  client?: { name: string; email: string; phone?: string };
}

export interface ApiError {
  error: { code: string; message: string };
}

export interface SessionUser {
  id: string;
  displayName: string;
  email: string;
}
