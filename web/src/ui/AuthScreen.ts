import { api, ApiError } from "../net/api";
import type { AuthUser } from "../types/contract";
import type { Screen } from "./Screen";
import { renderLogo } from "./Logo";

const USERNAME_RE = /^[a-zA-Z0-9_]{3,20}$/;

export class AuthScreen implements Screen {
  private el: HTMLElement | null = null;
  private mode: "login" | "register" = "login";
  private root: HTMLElement;
  private onAuthed: (user: AuthUser) => void;
  private playIntro: boolean;

  constructor(root: HTMLElement, onAuthed: (user: AuthUser) => void, playIntro = false) {
    this.root = root;
    this.onAuthed = onAuthed;
    this.playIntro = playIntro;
  }

  mount(): void {
    this.render();
  }

  unmount(): void {
    this.el?.remove();
    this.el = null;
  }

  getElement(): HTMLElement | null {
    return this.el;
  }

  private render(): void {
    this.el?.remove();

    const wrap = document.createElement("div");
    wrap.className = "centered-screen";
    wrap.appendChild(renderLogo(this.playIntro));

    const card = document.createElement("div");
    card.className = this.playIntro ? "card intro-reveal" : "card";
    wrap.appendChild(card);

    const subtitle = document.createElement("div");
    subtitle.className = "hint";
    subtitle.textContent = this.mode === "login" ? "Log in to play." : "Create an account.";
    card.appendChild(subtitle);

    const usernameField = this.buildField("username", "text");
    const passwordField = this.buildField("password", "password");
    card.appendChild(usernameField.field);
    card.appendChild(passwordField.field);

    const errorText = document.createElement("div");
    errorText.className = "error-text";
    card.appendChild(errorText);

    const validationHint = document.createElement("div");
    validationHint.className = "hint";
    validationHint.textContent =
      "Username: 3-20 chars, letters/numbers/underscore. Password: 8+ chars.";
    card.appendChild(validationHint);

    const submitBtn = document.createElement("button");
    submitBtn.className = "primary";
    submitBtn.textContent = this.mode === "login" ? "Log in" : "Register";
    card.appendChild(submitBtn);

    const switchBtn = document.createElement("button");
    switchBtn.className = "link-btn";
    switchBtn.textContent =
      this.mode === "login" ? "Need an account? Register" : "Already have an account? Log in";
    switchBtn.addEventListener("click", () => {
      this.mode = this.mode === "login" ? "register" : "login";
      this.render();
    });
    card.appendChild(switchBtn);

    const submit = async () => {
      errorText.textContent = "";
      const username = usernameField.input.value.trim();
      const password = passwordField.input.value;

      if (!USERNAME_RE.test(username)) {
        errorText.textContent = "Username must be 3-20 chars, letters/numbers/underscore only.";
        return;
      }
      if (password.length < 8) {
        errorText.textContent = "Password must be at least 8 characters.";
        return;
      }

      submitBtn.disabled = true;
      try {
        const user =
          this.mode === "login"
            ? await api.login(username, password)
            : await api.register(username, password);
        this.onAuthed(user);
      } catch (err) {
        errorText.textContent = err instanceof ApiError ? err.message : "Something went wrong.";
      } finally {
        submitBtn.disabled = false;
      }
    };

    submitBtn.addEventListener("click", () => void submit());
    passwordField.input.addEventListener("keydown", (e) => {
      if (e.key === "Enter") void submit();
    });

    this.root.appendChild(wrap);
    this.el = wrap;
    // Consumed after one render - the mode-switch button above calls
    // render() again, and that re-render must never replay the intro.
    this.playIntro = false;
  }

  private buildField(name: string, type: string): { field: HTMLElement; input: HTMLInputElement } {
    const field = document.createElement("div");
    field.className = "field";
    const label = document.createElement("label");
    label.textContent = name[0].toUpperCase() + name.slice(1);
    label.htmlFor = `auth-${name}`;
    const input = document.createElement("input");
    input.type = type;
    input.id = `auth-${name}`;
    input.autocomplete = type === "password" ? "current-password" : "username";
    field.append(label, input);
    return { field, input };
  }
}
