import { Link } from 'react-router-dom';

export default function NotFoundPage() {
  return (
    <div className="flex min-h-screen flex-col items-center justify-center px-6 text-center">
      <p className="text-6xl font-bold text-navy-200 dark:text-navy-700">404</p>
      <h1 className="mt-4 text-xl font-bold text-navy-900 dark:text-white">Page not found</h1>
      <p className="mt-2 max-w-sm text-sm text-navy-500 dark:text-navy-400">
        That page does not exist. It may have moved, or the link may be out of date.
      </p>
      <Link to="/app" className="btn-primary mt-6">
        Back to the dashboard
      </Link>
    </div>
  );
}
