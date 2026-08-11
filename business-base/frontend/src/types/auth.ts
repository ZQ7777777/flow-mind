export interface AuthenticatedUser {
  userId: string;
  username: string;
  realName: string;
  departmentId: string;
  departmentName: string;
  userType: "ADMIN" | "USER";
  administrator: boolean;
}

export interface LoginCredentials {
  username: string;
  password: string;
}
