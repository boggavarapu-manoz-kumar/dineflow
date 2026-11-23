import { describe, it, expect } from 'vitest';
import { render, screen } from '@testing-library/react';
import React from 'react';
import App from '../App';

describe('DineFlow Application Shell', () => {
  it('renders brand identity and lazy-loaded hero heading', async () => {
    render(<App />);
    const taglines = screen.getAllByText(/Scan\. Order\. Enjoy\./i);
    expect(taglines.length).toBeGreaterThanOrEqual(1);
    expect(await screen.findByRole('heading', { level: 1 })).toHaveTextContent(/Scan\. Order\./i);
  });

  it('renders all 5 application experience cards', async () => {
    render(<App />);
    expect(await screen.findByText(/1\. Customer App/i)).toBeInTheDocument();
    expect(screen.getByText(/2\. Hotel Admin/i)).toBeInTheDocument();
    expect(screen.getByText(/3\. Kitchen Display/i)).toBeInTheDocument();
    expect(screen.getByText(/4\. Waiter App/i)).toBeInTheDocument();
    expect(screen.getByText(/5\. SaaS Super Admin/i)).toBeInTheDocument();
  });

  it('renders the interactive workflow preview and hotel dashboard metrics', async () => {
    render(<App />);
    expect(await screen.findByText(/Zero Friction Table Ordering/i)).toBeInTheDocument();
    expect(screen.getByText(/Hotel Administration Operations/i)).toBeInTheDocument();
  });
});
