import History from './History';
import TaskFive from './TaskFive';
import SpeakingFlow from './SpeakingFlow';
import { prepareMicrophone, speakingTasks } from './speaking';
import type { Microphone, SpeakingSession } from './speaking';
import ReadingExam from './ReadingExam';
import ReadingRecovery from './ReadingRecovery';
import { readReadingRecovery } from './reading';
import type { ReadingSession } from './reading';
import WritingExam from "./WritingExam";
import WritingRecovery from "./WritingRecovery";
import { api, readRecovery } from "./practice";
import type { Mode, Session } from "./practice";
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
  const [speaking, setSpeaking] = useState<{session:SpeakingSession;mic:Microphone|null}>();
  const [session, setSession] = useState<Session | ReadingSession>();
  const [readingRecovery, setReadingRecovery] = useState(readReadingRecovery);
  const [recovery, setRecovery] = useState(readRecovery);
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

  function home() { setSpeaking(undefined); setSession(undefined); setRecovery(undefined); setReadingRecovery(undefined); window.location.hash = '/'; setPage(''); }
  async function start(packageId: string, mode: Mode, module: string, tasks: number[]) {
    if(module === 'SPEAKING') {
      if(!(tasks.join(',') === '3,4' || (tasks.length === 1 && speakingTasks.includes(tasks[0]) && ![3,4].includes(tasks[0])))) throw Error('This Speaking task is pending.')
      const mic = tasks[0] === 5 ? null : await prepareMicrophone()
      try {
        const active = await api<SpeakingSession>('/sessions','POST',{packageId,mode})
        setSpeaking({session:active,mic})
      } catch { mic?.release(); throw Error('Could not start the Speaking session. Check the local server. A lost response may leave an interrupted attempt; starting again creates a new attempt.') }
      return
    }
    const started = await api<Session & {timing: {module:string;answerSeconds:number}}>('/sessions', 'POST', { packageId, mode });
    try { sessionStorage.setItem(started.timing.module === 'READING' ? 'celpip-reading-recovery' : 'celpip-writing-recovery', JSON.stringify({ sessionId: started.sessionId, attemptId: started.attemptId, response: started.timing.module === 'READING' ? {answers:{}} : started.taskNumber === 2 ? { text: '', choice: null } : { text: '' } })); } catch { /* Server drafts still persist. */ }
    setSession(started);
  }
  if (speaking?.session.taskNumber === 5) return <TaskFive session={speaking.session} onHome={home} />;
  if (speaking?.mic) return <SpeakingFlow session={speaking.session} mic={speaking.mic} onHome={home} />;
  if (session) return 'passage' in session.content ? <ReadingExam session={session as ReadingSession} onHome={home} /> : <WritingExam session={session as Session} onHome={home} />;
  if (readingRecovery) return <ReadingRecovery recovery={readingRecovery} onHome={home} />;
  if (recovery) return <WritingRecovery recovery={recovery} onHome={home} />;
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
        ) : page === 'history' ? <History onStart={start} onHome={home}/> : ["reading", "writing", "speaking", "import"].includes(page) ? (
          <ImportScreen onStart={start} key={page} module={page === "import" ? undefined : page.toUpperCase()} />
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
