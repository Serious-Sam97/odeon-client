import { useEffect, useState } from "react";
import { api, type Filters, type Tag, type TagNamespace } from "./api";

const SORTS = [
  ["title", "título"],
  ["year", "ano"],
  ["added", "adicionado"],
  ["duration", "duração"],
  ["random", "aleatório"],
] as const;

/// Filtro composto sobre o grafo. Cada chip é `namespace:valor` — a mesma
/// string que vai pro backend, sem tradução no meio.
export default function FilterBar({
  filters,
  onChange,
}: {
  filters: Filters;
  onChange: (next: Filters) => void;
}) {
  const [tags, setTags] = useState<Tag[]>([]);
  const [namespaces, setNamespaces] = useState<TagNamespace[]>([]);
  const [open, setOpen] = useState(false);

  useEffect(() => {
    Promise.all([api.tags(), api.tagNamespaces()])
      .then(([t, n]) => {
        setTags(t.filter((x) => x.work_count > 0));
        setNamespaces(n);
      })
      .catch(() => {});
  }, [filters.collection]);

  const active = filters.tags ?? [];

  const toggle = (key: string) => {
    const next = active.includes(key) ? active.filter((t) => t !== key) : [...active, key];
    onChange({ ...filters, tags: next });
  };

  const grouped = namespaces
    .map((ns) => ({ ns, items: tags.filter((t) => t.namespace === ns.namespace) }))
    .filter((g) => g.items.length > 0);

  // namespaces que ninguém declarou em tag_namespace ainda aparecem
  const known = new Set(namespaces.map((n) => n.namespace));
  const extra = tags.filter((t) => !known.has(t.namespace));
  if (extra.length > 0) {
    grouped.push({
      ns: { namespace: "outros", label: "Outros", color: null, position: 999 },
      items: extra,
    });
  }

  const anyActive =
    active.length > 0 || filters.minMinutes || filters.yearFrom || filters.state;

  return (
    <div className="filterbar">
      <div className="filter-row">
        <button className="chip toggle" onClick={() => setOpen(!open)}>
          filtros {open ? "▴" : "▾"}
          {active.length > 0 && <span className="pill">{active.length}</span>}
        </button>

        <select
          className="select"
          value={filters.sort ?? "title"}
          onChange={(e) => onChange({ ...filters, sort: e.target.value })}
        >
          {SORTS.map(([value, label]) => (
            <option key={value} value={value}>
              ordenar por {label}
            </option>
          ))}
        </select>

        {active.length > 1 && (
          <button
            className="chip"
            onClick={() =>
              onChange({ ...filters, tagMode: filters.tagMode === "any" ? "all" : "any" })
            }
            title="alterna entre exigir todas as tags ou qualquer uma"
          >
            {filters.tagMode === "any" ? "qualquer tag" : "todas as tags"}
          </button>
        )}

        {anyActive && (
          <button
            className="chip clear"
            onClick={() => onChange({ q: filters.q, sort: filters.sort })}
          >
            limpar ✕
          </button>
        )}
      </div>

      {open && (
        <div className="filter-panel">
          {grouped.map(({ ns, items }) => (
            <div key={ns.namespace} className="filter-group">
              <span className="filter-label" style={{ color: ns.color ?? undefined }}>
                {ns.label}
              </span>
              <div className="chips">
                {items.map((t) => {
                  const key = `${t.namespace}:${t.value}`;
                  return (
                    <button
                      key={t.id}
                      className={active.includes(key) ? "chip on" : "chip"}
                      onClick={() => toggle(key)}
                      style={
                        active.includes(key) && ns.color
                          ? { borderColor: ns.color, color: ns.color }
                          : undefined
                      }
                    >
                      {t.value} <span className="muted">{t.work_count}</span>
                    </button>
                  );
                })}
              </div>
            </div>
          ))}

          <div className="filter-group">
            <span className="filter-label">Duração</span>
            <div className="chips">
              {[
                ["curto (até 40min)", { maxMinutes: 40, minMinutes: undefined }],
                ["médio (40–90min)", { minMinutes: 40, maxMinutes: 90 }],
                ["longo (90min+)", { minMinutes: 90, maxMinutes: undefined }],
              ].map(([label, patch]) => {
                const p = patch as Partial<Filters>;
                const on =
                  filters.minMinutes === p.minMinutes && filters.maxMinutes === p.maxMinutes;
                return (
                  <button
                    key={label as string}
                    className={on ? "chip on" : "chip"}
                    onClick={() =>
                      onChange(
                        on
                          ? { ...filters, minMinutes: undefined, maxMinutes: undefined }
                          : { ...filters, ...p },
                      )
                    }
                  >
                    {label as string}
                  </button>
                );
              })}
            </div>
          </div>

          <div className="filter-group">
            <span className="filter-label">Identificação</span>
            <div className="chips">
              {[
                ["confirmed", "confirmadas"],
                ["auto", "automáticas"],
                ["needs_review", "em dúvida"],
                ["unmatched", "sem match"],
              ].map(([value, label]) => (
                <button
                  key={value}
                  className={filters.state === value ? "chip on" : "chip"}
                  onClick={() =>
                    onChange({ ...filters, state: filters.state === value ? undefined : value })
                  }
                >
                  {label}
                </button>
              ))}
            </div>
          </div>
        </div>
      )}
    </div>
  );
}
