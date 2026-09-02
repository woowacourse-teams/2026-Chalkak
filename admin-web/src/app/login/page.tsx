import { Suspense } from "react";

import { LoginLoading, LoginScreen } from "@/features/auth/ui/login-screen";

export default function LoginPage() {
  return (
    <Suspense fallback={<LoginLoading />}>
      <LoginScreen />
    </Suspense>
  );
}
