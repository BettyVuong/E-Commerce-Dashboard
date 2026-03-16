import { render, screen, fireEvent, waitFor, act } from '@testing-library/react';
import userEvent from '@testing-library/user-event';
import { DataIngestion } from '../DataIngestion';
import '@testing-library/jest-dom';

// Mock the global fetch API
global.fetch = jest.fn() as jest.Mock;

describe('DataIngestion Component Unit Tests', () => {

    beforeEach(() => {
        jest.clearAllMocks();
        // Setup a mock for window.alert
        window.alert = jest.fn();
    });

    // HELPER 
    const simulateFileUpload = async (input: HTMLElement, file: File) => {
        await act(async () => {
            Object.defineProperty(input, 'files', { value: [file], configurable: true });
            fireEvent.change(input);
        });
        const uploadBtn = screen.getByRole('button', { name: /Upload and Clean Data/i });
        await waitFor(() => expect(uploadBtn).not.toBeDisabled());
    };

    // Initial Render
    test('renders the upload form by default', () => {
        render(<DataIngestion />);
        expect(screen.getByText(/File Upload \(.csv or .xlsx\)/i)).toBeInTheDocument();
    });

    // test for export logic - covers handleExport function
    test('calls alert when export button is clicked', async () => {
        const mockData = {
            entries: [{ invoice: '123', stockCode: 'ABC', description: 'T', quantity: 1, unitPrice: 10, customerId: 'C1', country: 'UK' }],
            totalEntries: 1,
            status: 'COMPLETED'
        };

        (global.fetch as jest.Mock).mockResolvedValue({
            ok: true,
            json: async () => (mockData)
        });

        render(<DataIngestion />);

        const file = new File(['test'], 'test.csv', { type: 'text/csv' });
        const input = screen.getByLabelText(/Select Data File:/i);

        await simulateFileUpload(input, file);

        await act(async () => {
            fireEvent.click(screen.getByRole('button', { name: /Upload and Clean Data/i }));
        });

        await waitFor(() => expect(screen.getByText(/Results/i)).toBeInTheDocument());

        const exportBtn = screen.getByRole('button', { name: /Download Cleaned Data/i });
        fireEvent.click(exportBtn);
        await waitFor(() => {
            expect(global.fetch).toHaveBeenCalledWith('/api/cleaning-data/cleaned/export');
        });
    });

    // test for pagination - covers handlePageChange and fetchResults
    test('updates data when Next page is clicked', async () => {
        (global.fetch as jest.Mock).mockResolvedValue({
            ok: true,
            json: async () => ({ entries: [], totalEntries: 50, status: 'COMPLETED' })
        });

        render(<DataIngestion />);

        const file = new File(['test'], 'test.csv', { type: 'text/csv' });
        const input = screen.getByLabelText(/Select Data File:/i);

        await simulateFileUpload(input, file);

        await act(async () => {
            fireEvent.click(screen.getByRole('button', { name: /Upload and Clean Data/i }));
        });

        await waitFor(() => expect(screen.getByText(/Results/i)).toBeInTheDocument());

        const nextBtn = screen.getByRole('button', { name: /Next/i });
        await act(async () => {
            fireEvent.click(nextBtn);
        });

        expect(global.fetch).toHaveBeenCalledWith(expect.stringContaining('page=1'));
    });

    // Tab Navigation Logic
    test('switches between Results tabs correctly', async () => {
        (global.fetch as jest.Mock).mockResolvedValue({
            ok: true,
            json: async () => ({ entries: [], totalEntries: 0, status: 'COMPLETED' })
        });

        render(<DataIngestion />);

        const file = new File(['test'], 'test.csv', { type: 'text/csv' });
        const input = screen.getByLabelText(/Select Data File:/i);

        await simulateFileUpload(input, file);

        await act(async () => {
            fireEvent.click(screen.getByRole('button', { name: /Upload and Clean Data/i }));
        });

        await waitFor(() => expect(screen.getByText(/Results/i)).toBeInTheDocument());

        const invalidTab = screen.getByRole('button', { name: /Invalid Items/i });
        fireEvent.click(invalidTab);
        expect(screen.getByText(/Could not clean \/ ERROR/i)).toBeInTheDocument();
    });

    // Mocking a successful data ingestion flow
    test('transitions to processing then results on successful upload', async () => {
        // mock every fetch call that happens in the sequence
        (global.fetch as jest.Mock)
            .mockResolvedValueOnce({ ok: true, text: async () => 'Upload Success' }) // Upload POST
            .mockResolvedValueOnce({ ok: true, json: async () => ({ jobId: '123' }) }) //  Cleaning POST
            .mockResolvedValueOnce({ ok: true, json: async () => ({ status: 'COMPLETED' }) }) // Polling status
            .mockResolvedValue({ // fetchResults (Multi calls happen here for Clean, Invalid, Dirty)
                ok: true, 
                json: async () => ({ entries: [], totalEntries: 0, status: 'COMPLETED' }) 
            });

        render(<DataIngestion />);
        const file = new File(['invoice,price\n1,10.0'], 'test.csv', { type: 'text/csv' });
        const input = screen.getByLabelText(/Select Data File:/i);
        await simulateFileUpload(input, file);

        await act(async () => {
            fireEvent.click(screen.getByRole('button', { name: /Upload and Clean Data/i }));
        });

        expect(screen.getByText(/Processing.../i)).toBeInTheDocument();

        await waitFor(() => {
            expect(screen.getByText(/Results/i)).toBeInTheDocument();
        }, { timeout: 5000 });
    });

    // Error Handling
    test('alerts user and reverts to upload step if API fails', async () => {
        (global.fetch as jest.Mock).mockRejectedValueOnce(new Error('Network Error'));

        render(<DataIngestion />);
        const file = new File(['test'], 'test.csv', { type: 'text/csv' });
        const input = screen.getByLabelText(/Select Data File:/i);
        await simulateFileUpload(input, file);

        await act(async () => {
            fireEvent.click(screen.getByRole('button', { name: /Upload and Clean Data/i }));
        });

        await waitFor(() => {
            expect(window.alert).toHaveBeenCalledWith(expect.stringContaining('Upload failed due to network error'));
            expect(screen.getByText(/File Upload \(.csv or .xlsx\)/i)).toBeInTheDocument();
        });
    });

    // provide mock data for all 3 tabs and clicking through them
    test('renders full data rows in all tabs', async () => {
        const mockFullData = {
            entries: [{
                invoice: 'INV001', stockCode: '85123A', description: 'TEST ITEM',
                quantity: 10, price: 2.5, customerId: '12345', country: 'UK',
                rawValues: JSON.stringify({ Invoice: 'INV001', Quantity: 10 }),
                reviewStatus: 'MANUAL_ERROR', reason: 'Missing Price'
            }],
            totalEntries: 1,
            status: 'COMPLETED'
        };

        (global.fetch as jest.Mock).mockResolvedValue({
            ok: true,
            json: async () => mockFullData
        });

        render(<DataIngestion />);

        const file = new File(['test'], 'test.csv', { type: 'text/csv' });
        const input = screen.getByLabelText(/Select Data File:/i);
        await simulateFileUpload(input, file);
        await act(async () => {
            fireEvent.click(screen.getByRole('button', { name: /Upload and Clean Data/i }));
        });

        await waitFor(() => expect(screen.getByText(/Results/i)).toBeInTheDocument());

        expect(screen.getByText('INV001')).toBeInTheDocument();
        expect(screen.getByText('TEST ITEM')).toBeInTheDocument();

        fireEvent.click(screen.getByRole('button', { name: /Invalid Items/i }));
        expect(screen.getByText(/Missing Price/i)).toBeInTheDocument();

        fireEvent.click(screen.getByRole('button', { name: /Raw \/ Uncleaned Data/i }));
        expect(screen.getByText(/Uncleaned Raw Data/i)).toBeInTheDocument();
    });

    // cover previous button logic
    test('covers previous page logic', async () => {
        const mockData = {
            entries: [],
            totalEntries: 50,
            status: 'COMPLETED'
        };

        (global.fetch as jest.Mock).mockResolvedValue({
            ok: true,
            json: async () => mockData
        });

        render(<DataIngestion />);

        const file = new File(['test'], 'test.csv', { type: 'text/csv' });
        const input = screen.getByLabelText(/Select Data File:/i);

        await simulateFileUpload(input, file);

        await act(async () => {
            fireEvent.click(screen.getByRole('button', { name: /Upload and Clean Data/i }));
        });

        await waitFor(() => expect(screen.getByText(/Results/i)).toBeInTheDocument());

        const nextBtn = screen.getByRole('button', { name: /Next/i });
        await act(async () => {
            fireEvent.click(nextBtn);
        });

        expect(global.fetch).toHaveBeenCalledWith(expect.stringContaining('page=1'));

        const prevBtn = screen.getByRole('button', { name: /Previous/i });
        expect(prevBtn).not.toBeDisabled();

        await act(async () => {
            fireEvent.click(prevBtn);
        });

        expect(global.fetch).toHaveBeenCalledWith(expect.stringContaining('page=0'));
        expect(screen.getByText(/Page 1/i)).toBeInTheDocument();
    });

    test('renders dirty data rows to cover table mapping logic', async () => {
        render(<DataIngestion />);

        const file = new File(['invoice,price\n1,10.0'], 'test.csv', { type: 'text/csv' });
        const input = screen.getByLabelText(/Select Data File:/i);

        await simulateFileUpload(input, file);

        (global.fetch as jest.Mock).mockResolvedValue({
            ok: true,
            json: async () => ({ entries: [], totalEntries: 0, status: 'COMPLETED' })
        });

        await act(async () => {
            fireEvent.click(screen.getByRole('button', { name: /Upload and Clean Data/i }));
        });

        await waitFor(() => expect(screen.getByText(/Results/i)).toBeInTheDocument());

        const dirtyTab = screen.getByRole('button', { name: /Raw \/ Uncleaned Data/i });
        fireEvent.click(dirtyTab);

        expect(screen.getByText(/Uncleaned Raw Data/i)).toBeInTheDocument();
    });
});