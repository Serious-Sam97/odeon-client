import { useEffect, useState } from "react";
import { api, API, auth, mixedContentProblem, setServer, type AuthUser } from "./api";

/// Tela de entrada. A mesma serve pra primeira execução (criar o admin) e pro
/// login — o servidor diz qual dos dois pela rota `/api/auth/status`.
export default function Login({ onAuthenticated }: { onAuthenticated: (u: AuthUser) => void }) {
  const [needsSetup, setNeedsSetup] = useState<boolean | null>(null);
  const [server, setServerInput] = useState(API);
  const [showServer, setShowServer] = useState(false);
  const [username, setUsername] = useState("");
  const [password, setPassword] = useState("");
  const [confirm, setConfirm] = useState("");
  const [error, setError] = useState<string | null>(null);
  const [busy, setBusy] = useState(false);

  useEffect(() => {
    const blocked = mixedContentProblem();
    if (blocked) {
      setError(blocked);
      setShowServer(true);
      setNeedsSetup(false);
      return;
    }
    api
      .authStatus()
      .then((s) => setNeedsSetup(s.needs_setup))
      .catch(() => {
        // Não alcançou o servidor: mostrar o campo é mais útil que um erro.
        setShowServer(true);
        setNeedsSetup(false);
      });
  }, []);

  const submit = async (e: React.FormEvent) => {
    e.preventDefault();
    setError(null);

    if (needsSetup && password !== confirm) {
      setError("as senhas não conferem");
      return;
    }

    // Trocar de servidor recarrega: o token vale por servidor.
    if (server.trim() !== API) {
      setServer(server);
      window.location.reload();
      return;
    }

    setBusy(true);
    try {
      const result = needsSetup
        ? await api.setup(username, password)
        : await api.login(username, password);
      auth.setToken(result.token);
      onAuthenticated(result.user);
    } catch (e) {
      // O servidor devolve a MESMA mensagem pra usuário inexistente e senha
      // errada — distinguir entregaria a lista de usuários válidos.
      setError(
        needsSetup
          ? e instanceof Error
            ? e.message
            : String(e)
          : "usuário ou senha incorretos",
      );
    } finally {
      setBusy(false);
    }
  };

  if (needsSetup === null) return null;

  return (
    <div className="login">
      <form className="login-card" onSubmit={submit}>
        <div className="brand">
          <span className="brand-mark">◉</span>
          <span className="brand-name">ODEON</span>
        </div>

        {needsSetup ? (
          <>
            <h1>Primeira execução</h1>
            <p className="muted small">
              Nenhuma senha definida ainda. Crie o administrador — depois disso esta tela some e
              só o login continua.
            </p>
          </>
        ) : (
          <h1>Entrar</h1>
        )}

        {showServer ? (
          <label>
            <span>servidor</span>
            <input
              value={server}
              onChange={(e) => setServerInput(e.target.value)}
              placeholder="https://odeon.SEU-TAILNET.ts.net:8443"
              autoCapitalize="off"
              autoCorrect="off"
              spellCheck={false}
            />
          </label>
        ) : (
          <button type="button" className="chip server-toggle" onClick={() => setShowServer(true)}>
            {API.replace(/^https?:\/\//, "")}
            {API.startsWith("https://") && <span className="lock" title="conexão segura">🔒</span>}
          </button>
        )}

        <label>
          <span>usuário</span>
          <input
            value={username}
            onChange={(e) => setUsername(e.target.value)}
            autoComplete="username"
            autoFocus
            required
          />
        </label>

        <label>
          <span>senha</span>
          <input
            type="password"
            value={password}
            onChange={(e) => setPassword(e.target.value)}
            autoComplete={needsSetup ? "new-password" : "current-password"}
            required
          />
        </label>

        {needsSetup && (
          <label>
            <span>repita a senha</span>
            <input
              type="password"
              value={confirm}
              onChange={(e) => setConfirm(e.target.value)}
              autoComplete="new-password"
              required
            />
          </label>
        )}

        {error && <p className="error small">{error}</p>}

        <button className="primary" type="submit" disabled={busy}>
          {busy ? "…" : needsSetup ? "criar administrador" : "entrar"}
        </button>

        {needsSetup && (
          <p className="muted small">Mínimo de 8 caracteres. A senha é cifrada com Argon2id.</p>
        )}
      </form>
    </div>
  );
}
