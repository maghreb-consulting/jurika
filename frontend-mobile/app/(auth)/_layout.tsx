import { Stack } from "expo-router";
import React from "react";
import { colors } from "../../theme/colors";

export default function AuthLayout() {
  return (
    <Stack
      screenOptions={{
        headerShown: false,
        contentStyle: { backgroundColor: colors.bg },
        animation: "slide_from_right",
      }}
    >
      <Stack.Screen name="workspace" />
      <Stack.Screen name="login" />
      <Stack.Screen name="twofa" />
    </Stack>
  );
}
