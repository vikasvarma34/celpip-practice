import ImportScreen from "./ImportScreen";
import { useEffect, useRef, useState } from "react";

type IconName =
  | "book"
  | "pen"
  | "mic"
  | "upload"
  | "history"
  | "arrow"
  | "chevron"
  | "back";
function Icon({
  name,
  className = "",
}: {
  name: IconName;
  className?: string;
}) {
  const paths: Record<IconName, React.ReactNode> = {
    book: (
      <>
        <path d="M12 5v15M3 4h5a4 4 0 0 1 4 4 4 4 0 0 1 4-4h5v15h-5a5 5 0 0 0-4 2 5 5 0 0 0-4-2H3V4Z" />
      </>
    ),
    pen: (
      <>
        <path d="m15 5 4 4M4 20l5-1L20 8a3 3 0 0 0-4-4L5 15l-1 5ZM4 20h16" />
      </>
    ),
    mic: (
      <>
        <rect x="9" y="2" width="6" height="13" rx="3" />
        <path d="M5 10v2a7 7 0 0 0 14 0v-2M12 19v3M8 22h8" />
      </>
    ),
    upload: (
      <>
        <path d="M12 16V3m-5 5 5-5 5 5M4 15v5a1 1 0 0 0 1 1h14a1 1 0 0 0 1-1v-5" />
      </>
    ),
    history: (
      <>
        <path d="M3 11a9 9 0 1 1 2 7M3 4v7h7M12 7v5l3 2" />
      </>
    ),
    arrow: <path d="M4 12h16m-6-6 6 6-6 6" />,
    chevron: <path d="m9 5 7 7-7 7" />,
    back: <path d="M20 12H4m6-6-6 6 6 6" />,
  };
  return (
    <svg
      className={className}
      width="24"
      height="24"
      viewBox="0 0 24 24"
      fill="none"
      stroke="currentColor"
      strokeWidth="1.8"
      strokeLinecap="round"
      strokeLinejoin="round"
      aria-hidden="true"
    >
      {paths[name]}
    </svg>
  );
}

const modules = [
  {
    id: "reading",
    title: "Reading",
    subtitle: "4 parts",
    icon: "book",
  },
  {
    id: "writing",
    title: "Writing",
    subtitle: "2 tasks",
    icon: "pen",
  },
  {
    id: "speaking",
    title: "Speaking",
    subtitle: "8 tasks",
    icon: "mic",
  },
] as const;
const pages: Record<string, string> = {
  reading: "Reading",
  writing: "Writing",
  speaking: "Speaking",
  import: "Load exercise",
  history: "Practice history",
};
const currentPage = () => window.location.hash.replace(/^#\/?/, "");

export default function App() {
  const [page, setPage] = useState(currentPage);
  const heading = useRef<HTMLHeadingElement>(null);
  const firstRender = useRef(true);
  useEffect(() => {
    const onHashChange = () => setPage(currentPage());
    window.addEventListener("hashchange", onHashChange);
    return () => window.removeEventListener("hashchange", onHashChange);
  }, []);
  useEffect(() => {
    document.title = "CELPIP Practice";
    if (firstRender.current) firstRender.current = false;
    else heading.current?.focus();
  }, [page]);

  return (
    <>
      <a
        className="skip-link"
        href="#main-content"
        onClick={(event) => {
          event.preventDefault();
          heading.current?.focus();
        }}
      >
        Skip to content
      </a>
      <header className="header">
        <div className="header-inner">
          <a className="brand" href="#/" aria-label="CELPIP Practice home">
            <span className="brand-icon">
              <img src="/maple-leaf.svg" width="40" height="40" alt="" />
            </span>
            <span>CELPIP Practice</span>
          </a>
        </div>
      </header>
      <main id="main-content" className="main">
        {!page ? (
          <>
            <section className="target-panel" aria-labelledby="target-heading">
              <h2 id="target-heading">My target</h2>
              <dl className="target-scores">
                {[
                  ["Listening", 10],
                  ["Reading", 10],
                  ["Writing", 9],
                  ["Speaking", 9],
                ].map(([label, score]) => (
                  <div key={label}>
                    <dt>{label}</dt>
                    <dd>{score}</dd>
                  </div>
                ))}
              </dl>
            </section>
            <section
              className="practice-section"
              aria-labelledby="practice-heading"
            >
              <h1 id="practice-heading" ref={heading} tabIndex={-1}>
                Choose your practice
              </h1>
              <div className="module-grid">
                {modules.map((module) => (
                  <a
                    className="module-card"
                    href={`#/${module.id}`}
                    key={module.id}
                  >
                    <span className="module-icon">
                      <Icon name={module.icon} />
                    </span>
                    <h2>{module.title}</h2>
                    <p>{module.subtitle}</p>
                    <span className="start-button">
                      Practise <Icon name="arrow" />
                    </span>
                  </a>
                ))}
              </div>
            </section>
            <div className="secondary-grid">
              <a className="secondary-card" href="#/import">
                <span className="secondary-icon">
                  <Icon name="upload" />
                </span>
                <span className="secondary-copy">
                  <span className="secondary-title">Load exercise</span>
                  <span className="secondary-subtitle">ZIP or JSON.</span>
                </span>
                <Icon name="chevron" className="secondary-chevron" />
              </a>
              <a className="secondary-card" href="#/history">
                <span className="secondary-icon">
                  <Icon name="history" />
                </span>
                <span className="secondary-copy">
                  <span className="secondary-title">Practice history</span>
                  <span className="secondary-subtitle">Previous attempts.</span>
                </span>
                <Icon name="chevron" className="secondary-chevron" />
              </a>
            </div>
          </>
        ) : ["reading", "writing", "speaking", "import"].includes(page) ? (
          <ImportScreen key={page} module={page === "import" ? undefined : page.toUpperCase()} />
        ) : (
          <section className="placeholder">
            <a className="back-link" href="#/">
              <Icon name="back" />
              Back to home
            </a>
            <h1 ref={heading} tabIndex={-1}>
              {pages[page] ?? "Page not found"}
            </h1>
            <p>
              {pages[page]
                ? "This screen will be available in a later update."
                : "Return to the homepage to choose a practice module."}
            </p>
          </section>
        )}
      </main>
    </>
  );
}
