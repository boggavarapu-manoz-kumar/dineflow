import { describe, it, expect } from 'vitest';
import { render, screen, fireEvent } from '@testing-library/react';
import React from 'react';
import { BrowserRouter } from 'react-router-dom';
import { AuthProvider } from '@/context/AuthContext';
import { StaffLoginPage } from '@/pages/auth/StaffLoginPage';
import { SuperAdminLoginPage } from '@/pages/auth/SuperAdminLoginPage';
import { CustomerEntryPage } from '@/pages/customer/CustomerEntryPage';

describe('DineFlow Sprint 2 Authentication Views', () => {
  it('renders Super Admin Login with dedicated enterprise layout', () => {
    render(
      <AuthProvider>
        <BrowserRouter>
          <SuperAdminLoginPage />
        </BrowserRouter>
      </AuthProvider>
    );

    expect(screen.getByText(/Platform Administration/i)).toBeInTheDocument();
    expect(screen.getByPlaceholderText(/superadmin@dineflow.io/i)).toBeInTheDocument();
    expect(screen.getByRole('button', { name: /Sign In as Super Admin/i })).toBeInTheDocument();
  });

  it('renders Hotel Staff Login with organization slug option and password toggle', () => {
    render(
      <AuthProvider>
        <BrowserRouter>
          <StaffLoginPage />
        </BrowserRouter>
      </AuthProvider>
    );

    expect(screen.getByText(/Hotel & Staff Portal/i)).toBeInTheDocument();
    expect(screen.getByPlaceholderText(/manager@hotel.com/i)).toBeInTheDocument();
    expect(screen.getByPlaceholderText(/e\.g\. grand-heritage/i)).toBeInTheDocument();
    expect(screen.getByRole('button', { name: /Sign In to Operations/i })).toBeInTheDocument();
  });

  it('renders Customer Guest Entry with zero password fields and table context', () => {
    render(
      <AuthProvider>
        <BrowserRouter>
          <CustomerEntryPage />
        </BrowserRouter>
      </AuthProvider>
    );

    expect(screen.getByText(/Welcome to The Grand Heritage/i)).toBeInTheDocument();
    expect(screen.getByText(/Table #04/i)).toBeInTheDocument();
    expect(screen.getByPlaceholderText(/e\.g\. Rahul Sharma/i)).toBeInTheDocument();
    expect(screen.getByPlaceholderText(/98765 43210/i)).toBeInTheDocument();

    // Verify zero password or OTP elements appear
    expect(screen.queryByLabelText(/password/i)).toBeNull();
    expect(screen.queryByText(/otp/i)).toBeNull();

    // Verify button
    expect(screen.getByRole('button', { name: /Continue to Menu/i })).toBeInTheDocument();
  });

  it('validates customer guest entry required fields', async () => {
    render(
      <AuthProvider>
        <BrowserRouter>
          <CustomerEntryPage />
        </BrowserRouter>
      </AuthProvider>
    );

    const submitBtn = screen.getByRole('button', { name: /Continue to Menu/i });
    fireEvent.click(submitBtn);

    expect(await screen.findByText(/Please enter your name to proceed/i)).toBeInTheDocument();
  });
});