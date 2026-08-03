/// R45 — o som de uma fita rebobinando, sintetizado.
///
/// ## Por que sintetizado
///
/// É a régua do §12, a mesma que recusou CDN de fonte e que fez o menu de DVD
/// tocar uma trilha sequenciada em vez de um `.ogg` (§47): **zero bytes**. Um
/// sample de rebobinar custaria uns 100 KB e uma licença pra alguém conferir;
/// isto custa umas cem linhas e nasce do navegador.
///
/// E é historicamente correto pelo mesmo motivo que a trilha do menu era: o som
/// de um VHS **não é uma gravação** — é um motor e um atrito. Sintetizar é
/// descrever o mecanismo, que é o que este projeto vem fazendo desde a §35
/// quando transformou "formato" em comportamento.
///
/// ## Como o som é feito
///
/// Três camadas, e cada uma é uma peça do aparelho:
///
/// | camada | o que é no objeto |
/// |---|---|
/// | ruído branco filtrado | a fita raspando na cabeça e nas guias |
/// | dente de serra grave | o motor puxando o carretel |
/// | seno agudo, baixinho | o assobio da engrenagem em rotação alta |
///
/// As três respondem à **velocidade**, que quem chama atualiza a cada quadro:
/// o filtro abre, o motor sobe de tom e o assobio aparece. É o mesmo dado que
/// gira os carretéis na tela — som e imagem contam a mesma coisa, e é por isso
/// que o conjunto convence.
export class RuidoDeFita {
  private ctx: AudioContext | null = null;
  private mestre: GainNode | null = null;
  private filtro: BiquadFilterNode | null = null;
  private motor: OscillatorNode | null = null;
  private assobioGanho: GainNode | null = null;

  /// Liga. Só funciona dentro de um gesto — e é sempre o caso: rebobinar é um
  /// clique.
  comecar() {
    if (this.ctx) return;
    const Ctx =
      window.AudioContext ??
      (window as unknown as { webkitAudioContext?: typeof AudioContext }).webkitAudioContext;
    if (!Ctx) return;

    const ctx = new Ctx();
    this.ctx = ctx;

    const mestre = ctx.createGain();
    // Baixo de propósito: é ruído de fundo de um aparelho na sala, não um
    // efeito sonoro de jogo. Alto demais, o gesto vira piada.
    mestre.gain.value = 0.0001;
    mestre.connect(ctx.destination);
    this.mestre = mestre;

    // --- a fita raspando: ruído branco por um passa-faixa
    const segundos = 2;
    const buffer = ctx.createBuffer(1, ctx.sampleRate * segundos, ctx.sampleRate);
    const dados = buffer.getChannelData(0);
    for (let i = 0; i < dados.length; i++) dados[i] = Math.random() * 2 - 1;

    const filtro = ctx.createBiquadFilter();
    filtro.type = "bandpass";
    filtro.frequency.value = 1200;
    // Q baixo: um passa-faixa estreito vira apito, e fita não apita — chia.
    filtro.Q.value = 0.7;
    filtro.connect(mestre);
    this.filtro = filtro;

    const fonte = ctx.createBufferSource();
    fonte.buffer = buffer;
    fonte.loop = true;
    fonte.connect(filtro);
    fonte.start();

    // --- o motor
    const motor = ctx.createOscillator();
    motor.type = "sawtooth";
    motor.frequency.value = 78;
    const motorGanho = ctx.createGain();
    motorGanho.gain.value = 0.12;
    motor.connect(motorGanho).connect(mestre);
    motor.start();
    this.motor = motor;

    // --- o assobio da engrenagem, que só aparece em rotação alta
    const assobio = ctx.createOscillator();
    assobio.type = "sine";
    assobio.frequency.value = 2100;
    const assobioGanho = ctx.createGain();
    assobioGanho.gain.value = 0;
    assobio.connect(assobioGanho).connect(mestre);
    assobio.start();
    this.assobioGanho = assobioGanho;

    // Sobe em 120ms. Um motor que começa no volume final lê como corte de
    // áudio, não como aparelho ligando.
    mestre.gain.exponentialRampToValueAtTime(0.075, ctx.currentTime + 0.12);
  }

  /// A velocidade, de 0 a 1 — a mesma que gira os carretéis.
  velocidade(v: number) {
    const ctx = this.ctx;
    if (!ctx || !this.filtro || !this.motor || !this.assobioGanho) return;
    const t = ctx.currentTime;
    const k = Math.max(0, Math.min(1, v));
    // Rampas curtas em vez de atribuição direta: mudar o valor de um oscilador
    // no meio do ciclo estala.
    this.filtro.frequency.linearRampToValueAtTime(900 + k * 1700, t + 0.08);
    this.motor.frequency.linearRampToValueAtTime(70 + k * 70, t + 0.08);
    this.assobioGanho.gain.linearRampToValueAtTime(k > 0.55 ? (k - 0.55) * 0.05 : 0, t + 0.08);
  }

  /// Para — **com o tranco**.
  ///
  /// O §4.5 pediu *"a parada seca com um pulo de um quadro"*, e o som é metade
  /// dela: o motor cai de tom em 90ms enquanto o chiado some, e por cima entra
  /// um baque grave curto — o mecanismo batendo no fim de curso. Sem o baque, o
  /// silêncio lê como o áudio tendo acabado, não como a fita tendo chegado.
  parar() {
    const ctx = this.ctx;
    if (!ctx || !this.mestre) return;
    const t = ctx.currentTime;

    this.motor?.frequency.exponentialRampToValueAtTime(38, t + 0.09);
    this.assobioGanho?.gain.linearRampToValueAtTime(0, t + 0.05);
    this.filtro?.frequency.exponentialRampToValueAtTime(220, t + 0.09);
    this.mestre.gain.exponentialRampToValueAtTime(0.0001, t + 0.14);

    // O baque.
    const baque = ctx.createOscillator();
    baque.type = "sine";
    baque.frequency.setValueAtTime(120, t);
    baque.frequency.exponentialRampToValueAtTime(46, t + 0.12);
    const g = ctx.createGain();
    g.gain.setValueAtTime(0.0001, t);
    g.gain.exponentialRampToValueAtTime(0.16, t + 0.012);
    g.gain.exponentialRampToValueAtTime(0.0001, t + 0.2);
    baque.connect(g).connect(ctx.destination);
    baque.start(t);
    baque.stop(t + 0.22);

    // Fecha o contexto depois do baque. Um `AudioContext` por rebobinar, e
    // nenhum vivo depois — o navegador limita quantos existem ao mesmo tempo.
    const morrer = this.ctx;
    window.setTimeout(() => void morrer?.close().catch(() => {}), 400);
    this.ctx = null;
    this.mestre = null;
    this.filtro = null;
    this.motor = null;
    this.assobioGanho = null;
  }

  /// Corta sem tranco — pra quando a tela fecha no meio.
  cortar() {
    const morrer = this.ctx;
    this.ctx = null;
    void morrer?.close().catch(() => {});
  }
}
