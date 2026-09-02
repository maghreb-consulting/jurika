export interface ApiError {
  code: string;
  message: string;
  timestamp?: string;
  errors?: { field: string; message: string }[];
}
