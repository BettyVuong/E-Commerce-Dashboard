import React from "react";
import { render, screen, fireEvent, waitFor } from "@testing-library/react";
import '@testing-library/jest-dom'
import { DataIngestion } from "../DataIngestion";

//const declared here
const mockFetch = jest.fn();//mock function that replaces browser fetch
const mockCleanData = {
        entries: [{ invoice: '581492', stockCode: '22995', description: 'TRAVEL CARD WALLET SUKI', quantity: 4, price: 0.83, customerId: '' }],
        totalEntries: 1
    };

const file = new File(['a,b'], 'test.csv', { type: 'text/csv' });
//checks to see if alert function is called and replaces it with a blank function
const alert = jest.spyOn(window, 'alert').mockImplementation(() => {});

beforeEach(() => {
    global.fetch = mockFetch
});

afterEach(() => {
    mockFetch.mockReset();
    jest.useFakeTimers();//'freezes time after each test'
});
/**
 *
 * This test uses a mocked version of fetch to simulate a network call to /api/ingest/upload.
 * and then a second call to /api/cleaning-jobs immediately after the upload succeeds.
 * It selects a file, clicks submit, and checks that the first fetch call was
 * made to /api/ingest/upload with a POST method. And the second was to /api/cleaning-job with a POST Method
 */
test('Fetch upload API and Cleaning API', async () => {
    mockFetch
    .mockResolvedValueOnce({ ok: true, text: async() => ''})//upload
    .mockResolvedValueOnce({ ok: true, json: async() =>({jobId: '1'}) });//cleaning job
    render(<DataIngestion />);

   fireEvent.change(screen.getByLabelText('Select Data File:'), { target: { files: [file] } });
   fireEvent.click(screen.getByText('Upload and Clean Data'))
    await waitFor(() => {
        expect(mockFetch).toHaveBeenNthCalledWith(1, '/api/ingest/upload', expect.objectContaining({ method: 'POST'}))
        expect(mockFetch).toHaveBeenNthCalledWith(2, '/api/cleaning-jobs', expect.objectContaining({ method: 'POST'}))

    });
});

/**
 * This test simulates a full job completion flow:
 * 1. POST /api/ingest/upload - uploads the file
 * 2. POST /api/cleaning-jobs - creates the cleaning job
 * 3. GET /api/cleaning-jobs/{jobId} - retrieves the cleaning job status
 * 4. GET /api/cleaning-data/cleaned - retrieves the cleaned data
 * and checks that the clean data is rendered in the table after the job completes.
 * timeout is used to give the polling(status) more time before failing the test
 */
test('renders clean data after job completes', async () => {
    mockFetch
        .mockResolvedValueOnce({ ok: true, text: async() => ''}) //POST upload
        .mockResolvedValueOnce({ ok: true, json: async() =>({jobId: '1'}) }) //POST cleaning-jobs
        .mockResolvedValueOnce({ ok: true, json: async() =>({status: 'COMPLETED'}) }) // GETcleaning job status
        .mockResolvedValue({ok: true, json: async () => mockCleanData})//GET cleaned data

    render(<DataIngestion/>);
    fireEvent.change(screen.getByLabelText('Select Data File:'), { target: { files: [file] } })
    fireEvent.click(screen.getByText('Upload and Clean Data'))

    await waitFor(() => {
        expect(screen.getByText('581492')).toBeInTheDocument()
    } ,{timeout: 5000});

});

/**
 * This test simulates a faling cleaning job
 * 1. POST /api/ingest/upload - uploads the file
 * 2. POST /api/cleaning-jobs - creates the cleaning job
 * 3. GET /api/cleaning-jobs/{jobId} - retrieves the cleaning job status(FAILED)
 * alert will be called with the error message that is being checked for
 */
test('Shows error during cleaning job', async () => {

    mockFetch
        .mockResolvedValueOnce({ ok: true, text: async() => ''}) //POST upload
        .mockResolvedValueOnce({ ok: true, json: async() =>({jobId: '1'}) }) //POST cleaning-jobs
        .mockResolvedValueOnce({ ok: true, json: async() =>({status: 'FAILED'}) }) // GETcleaning job status

    render(<DataIngestion/>);
    fireEvent.change(screen.getByLabelText('Select Data File:'), { target: { files: [file] } })
    fireEvent.click(screen.getByText('Upload and Clean Data'))

    await waitFor(() => {
        expect(alert).toHaveBeenCalledWith('Cleaning job failed with status: FAILED')
    } ,{timeout: 5000});

});

/**
 * renders the data ingestion section\
 * uses the alert test double to receive message for no file selected.
 */
test('Shows alert whenn no file is selected', async () => {
    render(<DataIngestion />);
    fireEvent.click(screen.getByText('Upload and Clean Data'));
    expect(alert).toHaveBeenCalledWith('Please select a file to upload first.');
});