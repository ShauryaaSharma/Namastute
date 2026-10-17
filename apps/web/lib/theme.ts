// Dark or light (Learning Notes 70). The choice is kept on this device only; "system" follows the phone's setting.
// The script in app/layout.tsx applies it before the first paint; this file changes it later.
export type ThemeChoice = "system" | "dark" | "light";

export function themeChoice(): ThemeChoice {
  try {
    const saved = localStorage.getItem("theme");
    return saved === "dark" || saved === "light" ? saved : "system";
  } catch {
    return "system"; // storage can be blocked (private mode); the page still works
  }
}

export function setThemeChoice(choice: ThemeChoice): void {
  try {
    if (choice === "system") localStorage.removeItem("theme");
    else localStorage.setItem("theme", choice);
  } catch {
    // not saved, but still applied below for this visit
  }
  const dark = choice === "system" ? !matchMedia("(prefers-color-scheme: light)").matches : choice === "dark";
  document.documentElement.classList.toggle("dark", dark);
}
