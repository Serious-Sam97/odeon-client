import { StrictMode } from "react";
import { createRoot } from "react-dom/client";
import { BrowserRouter } from "react-router-dom";
import App from "./App";
import "./styles.css";

/// R43 — o endereço.
///
/// Até aqui as telas eram **estado de aba**: nada no Odeon tinha endereço, e o
/// perfil de alguém só se alcançava clicando. O §4.1 pediu *"um link que dá pra
/// mandar"*, e quem decide pediu mais que isso — *"eu quero futuramente tudo
/// linkável, então já coloca"*.
///
/// Daí o router de verdade, e não um `pushState` de vinte linhas: um perfil
/// endereçável se resolve à mão, mas *todas* as telas endereçáveis — com
/// filtro, histórico e voltar — é reescrever um router pior. A dependência é a
/// quarta deste front, e entra sabendo disso.
///
/// **`BrowserRouter` cobra o seu preço no servidor**: um caminho que não é
/// arquivo precisa devolver o `index.html`. O Vite do desenvolvimento já faz;
/// o nginx do estágio de produção passou a fazer (`web/nginx.conf`).
createRoot(document.getElementById("root")!).render(
  <StrictMode>
    <BrowserRouter>
      <App />
    </BrowserRouter>
  </StrictMode>,
);
