import { render, screen } from '@testing-library/react';
import { describe, expect, it, vi } from 'vitest';

import { Banner, ConfirmDialog, EmptyState, RiskBadge, StatTile } from '../ui';

describe('StatTile', () => {
  it('renders the label and value', () => {
    render(<StatTile label="Total balance" value="₹1,000.00" />);

    expect(screen.getByText('Total balance')).toBeInTheDocument();
    expect(screen.getByText('₹1,000.00')).toBeInTheDocument();
  });

  it('renders an optional hint', () => {
    render(<StatTile label="Net" value="₹50" hint="Across 2 accounts" />);
    expect(screen.getByText('Across 2 accounts')).toBeInTheDocument();
  });
});

describe('RiskBadge', () => {
  it('shows the band and the score together', () => {
    render(<RiskBadge level="HIGH" score={72} />);

    expect(screen.getByText('HIGH')).toBeInTheDocument();
    expect(screen.getByText('72')).toBeInTheDocument();
  });

  it('renders nothing when there is no band', () => {
    const { container } = render(<RiskBadge level={null} />);
    expect(container).toBeEmptyDOMElement();
  });
});

describe('Banner', () => {
  it('announces itself to assistive technology', () => {
    render(<Banner tone="error">Something went wrong</Banner>);

    const alert = screen.getByRole('alert');
    expect(alert).toHaveTextContent('Something went wrong');
  });

  it('renders an optional title', () => {
    render(
      <Banner tone="warning" title="KYC incomplete">
        Transfers are locked
      </Banner>,
    );

    expect(screen.getByText('KYC incomplete')).toBeInTheDocument();
    expect(screen.getByText('Transfers are locked')).toBeInTheDocument();
  });
});

describe('EmptyState', () => {
  it('explains what is missing rather than showing a blank box', () => {
    render(<EmptyState title="No payees yet" message="Add someone to send money to." />);

    expect(screen.getByText('No payees yet')).toBeInTheDocument();
    expect(screen.getByText('Add someone to send money to.')).toBeInTheDocument();
  });
});

describe('ConfirmDialog', () => {
  it('renders nothing when closed', () => {
    const { container } = render(
      <ConfirmDialog
        open={false}
        title="Remove payee?"
        message="This cannot be undone."
        onConfirm={vi.fn()}
        onCancel={vi.fn()}
      />,
    );

    expect(container).toBeEmptyDOMElement();
  });

  it('is a modal dialog when open', () => {
    render(
      <ConfirmDialog
        open
        title="Remove payee?"
        message="This cannot be undone."
        onConfirm={vi.fn()}
        onCancel={vi.fn()}
      />,
    );

    const dialog = screen.getByRole('dialog');
    expect(dialog).toHaveAttribute('aria-modal', 'true');
    expect(screen.getByText('Remove payee?')).toBeInTheDocument();
  });

  it('offers a cancel path alongside the confirm action', () => {
    render(
      <ConfirmDialog
        open
        title="Abandon goal?"
        message="Progress is kept."
        confirmLabel="Abandon"
        onConfirm={vi.fn()}
        onCancel={vi.fn()}
      />,
    );

    expect(screen.getByRole('button', { name: 'Cancel' })).toBeInTheDocument();
    expect(screen.getByRole('button', { name: 'Abandon' })).toBeInTheDocument();
  });
});
