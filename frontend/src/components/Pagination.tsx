import { ChevronLeft, ChevronRight } from 'lucide-react';

interface PaginationProps {
  page: number;
  totalPages: number;
  totalElements: number;
  first: boolean;
  last: boolean;
  onPageChange: (page: number) => void;
}

export function Pagination({
  page,
  totalPages,
  totalElements,
  first,
  last,
  onPageChange,
}: PaginationProps) {
  if (totalElements === 0) {
    return null;
  }

  return (
    <nav
      className="flex items-center justify-between border-t border-slate-200 px-4 py-3 text-sm"
      aria-label="Pagination"
    >
      <p className="text-slate-600">
        Page <span className="font-medium text-slate-900">{page + 1}</span> of{' '}
        <span className="font-medium text-slate-900">{Math.max(totalPages, 1)}</span> ·{' '}
        {totalElements} {totalElements === 1 ? 'entry' : 'entries'}
      </p>

      <div className="flex gap-2">
        <button
          type="button"
          disabled={first}
          onClick={() => {
            onPageChange(page - 1);
          }}
          className="inline-flex items-center gap-1 rounded-md border border-slate-300 bg-white px-3 py-1.5 font-medium text-slate-700 hover:bg-slate-50 disabled:cursor-not-allowed disabled:opacity-40"
        >
          <ChevronLeft className="h-4 w-4" aria-hidden="true" />
          Previous
        </button>
        <button
          type="button"
          disabled={last}
          onClick={() => {
            onPageChange(page + 1);
          }}
          className="inline-flex items-center gap-1 rounded-md border border-slate-300 bg-white px-3 py-1.5 font-medium text-slate-700 hover:bg-slate-50 disabled:cursor-not-allowed disabled:opacity-40"
        >
          Next
          <ChevronRight className="h-4 w-4" aria-hidden="true" />
        </button>
      </div>
    </nav>
  );
}
