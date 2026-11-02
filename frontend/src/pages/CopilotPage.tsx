import { useEffect, useRef, useState } from 'react';
import { Bot, Loader2, Send, Sparkles, User } from 'lucide-react';
import {
  Bar,
  BarChart,
  CartesianGrid,
  Cell,
  Line,
  LineChart,
  Pie,
  PieChart,
  ResponsiveContainer,
  Tooltip,
  XAxis,
  YAxis,
} from 'recharts';

import { errorMessage } from '../api/client';
import { intelligence } from '../api/endpoints';
import type { CopilotAnswer } from '../api/types';
import { Banner, PageHeader } from '../components/ui';
import { money } from '../lib/format';

interface Turn {
  role: 'user' | 'assistant';
  text: string;
  answer?: CopilotAnswer;
}

const CHART_COLOURS = ['#0e234c', '#04c8b1', '#466fa9', '#e8a33d', '#688dc1', '#058076'];

/**
 * The AI Financial Copilot.
 *
 * Each answer shows the figures it was computed from and, where it helps, a
 * chart. The generator is displayed honestly: when the answer came from the
 * deterministic analytics engine rather than a language model, it says so.
 */
export default function CopilotPage() {
  const [turns, setTurns] = useState<Turn[]>([]);
  const [question, setQuestion] = useState('');
  const [suggestions, setSuggestions] = useState<string[]>([]);
  const [conversationId, setConversationId] = useState<number | null>(null);
  const [busy, setBusy] = useState(false);
  const [error, setError] = useState('');
  const endRef = useRef<HTMLDivElement>(null);

  useEffect(() => {
    intelligence
      .suggestions()
      .then(setSuggestions)
      .catch(() => setSuggestions([]));
  }, []);

  useEffect(() => {
    endRef.current?.scrollIntoView({ behavior: 'smooth' });
  }, [turns, busy]);

  async function ask(text: string) {
    const trimmed = text.trim();
    if (!trimmed || busy) return;

    setError('');
    setQuestion('');
    setTurns((current) => [...current, { role: 'user', text: trimmed }]);
    setBusy(true);
    try {
      const answer = await intelligence.ask(trimmed, conversationId);
      setConversationId(answer.conversationId);
      setTurns((current) => [...current, { role: 'assistant', text: answer.answer, answer }]);
    } catch (caught) {
      setError(errorMessage(caught, 'The copilot could not answer that.'));
    } finally {
      setBusy(false);
    }
  }

  return (
    <>
      <PageHeader
        title="Financial copilot"
        subtitle="Ask about your spending, balances, health score or goals. Answers come from your own transactions."
      />

      <div className="mx-auto max-w-3xl">
        {error && (
          <div className="mb-4">
            <Banner tone="error">{error}</Banner>
          </div>
        )}

        <div className="card min-h-[22rem]">
          {turns.length === 0 ? (
            <div className="py-6 text-center">
              <div className="mx-auto mb-3 inline-flex rounded-2xl bg-navy-900 p-3 dark:bg-teal-500">
                <Sparkles className="h-6 w-6 text-white dark:text-navy-950" aria-hidden />
              </div>
              <p className="font-semibold text-navy-900 dark:text-white">
                Ask me about your finances
              </p>
              <p className="mx-auto mt-1.5 max-w-md text-sm text-navy-500 dark:text-navy-400">
                I compute every answer from your own transaction records, and I show the figures
                behind it so you can check the arithmetic.
              </p>

              <div className="mt-6 grid gap-2 text-left sm:grid-cols-2">
                {suggestions.map((suggestion) => (
                  <button
                    key={suggestion}
                    type="button"
                    className="rounded-xl border border-navy-200 px-3.5 py-2.5 text-left text-sm text-navy-700 transition hover:border-teal-500 hover:bg-navy-50 dark:border-navy-700 dark:text-navy-200 dark:hover:bg-navy-800"
                    onClick={() => ask(suggestion)}
                  >
                    {suggestion}
                  </button>
                ))}
              </div>
            </div>
          ) : (
            <ul className="space-y-5">
              {turns.map((turn, index) => (
                <li key={index} className="flex gap-3">
                  <span
                    className={`inline-flex h-8 w-8 shrink-0 items-center justify-center rounded-full ${
                      turn.role === 'user'
                        ? 'bg-navy-100 text-navy-700 dark:bg-navy-800 dark:text-navy-200'
                        : 'bg-navy-900 text-white dark:bg-teal-500 dark:text-navy-950'
                    }`}
                  >
                    {turn.role === 'user' ? (
                      <User className="h-4 w-4" aria-hidden />
                    ) : (
                      <Bot className="h-4 w-4" aria-hidden />
                    )}
                  </span>

                  <div className="min-w-0 flex-1">
                    <p className="text-sm leading-relaxed text-navy-800 dark:text-navy-100">
                      {turn.text}
                    </p>

                    {turn.answer && Object.keys(turn.answer.figures).length > 0 && (
                      <dl className="mt-3 grid gap-x-6 gap-y-1.5 rounded-xl bg-navy-50 p-3.5 text-sm sm:grid-cols-2 dark:bg-navy-950">
                        {Object.entries(turn.answer.figures).map(([label, value]) => (
                          <div key={label} className="flex justify-between gap-3">
                            <dt className="truncate text-navy-500 dark:text-navy-400">{label}</dt>
                            <dd className="tabular shrink-0 font-semibold text-navy-900 dark:text-white">
                              {value}
                            </dd>
                          </div>
                        ))}
                      </dl>
                    )}

                    {turn.answer?.chart && <CopilotChart chart={turn.answer.chart} />}

                    {turn.answer && (
                      <p className="mt-2 text-xs text-navy-400 dark:text-navy-500">
                        {turn.answer.intent !== 'UNKNOWN' && <>Intent: {turn.answer.intent} · </>}
                        {turn.answer.generator === 'rule_engine'
                          ? 'Computed by the analytics engine from your transactions'
                          : 'Phrased by a language model from computed figures'}
                        {!turn.answer.dataAvailable && ' · limited data available'}
                      </p>
                    )}
                  </div>
                </li>
              ))}

              {busy && (
                <li className="flex gap-3">
                  <span className="inline-flex h-8 w-8 shrink-0 items-center justify-center rounded-full bg-navy-900 text-white dark:bg-teal-500 dark:text-navy-950">
                    <Bot className="h-4 w-4" aria-hidden />
                  </span>
                  <span className="pt-1.5 text-sm text-navy-500 dark:text-navy-400">
                    Working through your transactions…
                  </span>
                </li>
              )}
            </ul>
          )}
          <div ref={endRef} />
        </div>

        <form
          onSubmit={(event) => {
            event.preventDefault();
            ask(question);
          }}
          className="mt-4 flex gap-2"
        >
          <input
            className="input flex-1"
            value={question}
            maxLength={500}
            onChange={(event) => setQuestion(event.target.value)}
            placeholder="How much did I spend on food this month?"
            aria-label="Your question"
          />
          <button type="submit" className="btn-primary" disabled={busy || !question.trim()}>
            {busy ? (
              <Loader2 className="h-4 w-4 animate-spin" aria-hidden />
            ) : (
              <Send className="h-4 w-4" aria-hidden />
            )}
            Ask
          </button>
        </form>

        {turns.length > 0 && suggestions.length > 0 && (
          <div className="mt-3 flex flex-wrap gap-2">
            {suggestions.slice(0, 4).map((suggestion) => (
              <button
                key={suggestion}
                type="button"
                className="rounded-full border border-navy-200 px-3 py-1 text-xs text-navy-600 hover:bg-navy-50 dark:border-navy-700 dark:text-navy-300 dark:hover:bg-navy-800"
                onClick={() => ask(suggestion)}
              >
                {suggestion}
              </button>
            ))}
          </div>
        )}
      </div>
    </>
  );
}

function CopilotChart({
  chart,
}: {
  chart: { type: string; title: string; labels: string[]; values: number[] };
}) {
  const data = chart.labels.map((label, index) => ({
    name: label,
    value: chart.values[index] ?? 0,
  }));

  return (
    <figure className="mt-3 rounded-xl border border-navy-100 p-3 dark:border-navy-800">
      <figcaption className="mb-2 text-xs font-semibold text-navy-600 dark:text-navy-300">
        {chart.title}
      </figcaption>
      <div className="h-52">
        <ResponsiveContainer width="100%" height="100%">
          {chart.type === 'pie' ? (
            <PieChart>
              <Pie data={data} dataKey="value" nameKey="name" outerRadius={72} paddingAngle={2}>
                {data.map((_, index) => (
                  <Cell key={index} fill={CHART_COLOURS[index % CHART_COLOURS.length]} />
                ))}
              </Pie>
              <Tooltip formatter={(value: number) => money(value)} />
            </PieChart>
          ) : chart.type === 'line' ? (
            <LineChart data={data} margin={{ top: 4, right: 8, bottom: 0, left: 0 }}>
              <CartesianGrid strokeDasharray="3 3" stroke="#e3eaf5" vertical={false} />
              <XAxis dataKey="name" tick={{ fontSize: 11 }} />
              <YAxis tick={{ fontSize: 11 }} width={56} />
              <Tooltip formatter={(value: number) => money(value)} />
              <Line type="monotone" dataKey="value" stroke="#04c8b1" strokeWidth={2.5} dot={false} />
            </LineChart>
          ) : (
            <BarChart data={data} margin={{ top: 4, right: 8, bottom: 0, left: 0 }}>
              <CartesianGrid strokeDasharray="3 3" stroke="#e3eaf5" vertical={false} />
              <XAxis dataKey="name" tick={{ fontSize: 11 }} />
              <YAxis tick={{ fontSize: 11 }} width={56} />
              <Tooltip formatter={(value: number) => money(value)} />
              <Bar dataKey="value" radius={[6, 6, 0, 0]} fill="#0e234c" />
            </BarChart>
          )}
        </ResponsiveContainer>
      </div>
    </figure>
  );
}
