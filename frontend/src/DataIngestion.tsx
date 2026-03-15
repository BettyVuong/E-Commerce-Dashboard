import React, { useState } from 'react';

// Components
import { RFMScatterPlot } from './components/RFMScatterPlot';

type PrimitiveCell = string | number | boolean | null | undefined;

interface DirtyRow {
    invoice?: PrimitiveCell;
    stockCode?: PrimitiveCell;
    description?: PrimitiveCell;
    quantity?: PrimitiveCell;
    invoiceDate?: PrimitiveCell;
    price?: PrimitiveCell;
    customerId?: PrimitiveCell;
    country?: PrimitiveCell;
}

interface CleanRow {
    invoice?: string;
    stockCode?: string;
    description?: string;
    quantity?: PrimitiveCell;
    price?: PrimitiveCell;
    customerId?: PrimitiveCell;
    invoiceDate?: string; // to get the date range for the rfm scatter plot
    country?: string; // to get the country for the rfm scatter plot
}

interface InvalidRow {
    rawValues?: string;
    reviewStatus?: string;
    reason?: string;
    validationErrors?: string;
}

type ResultsTab = 'clean' | 'invalid' | 'rfm-scatter-results' | 'dirty';

const parseRawValues = (rawValues?: string): Record<string, PrimitiveCell> => {
    if (!rawValues) {
        return {};
    }

    try {
        const parsed = JSON.parse(rawValues) as unknown;
        return (parsed && typeof parsed === 'object') ? (parsed as Record<string, PrimitiveCell>) : {};
    } catch {
        return {};
    }
};

// Helper to get the date range from the clean data
// walks through the clean data and finds the min and max invoiceDate to determine the date range for the rfm scatter plot
const deriveDateRange = (rows: CleanRow[]): { startDate: string; endDate: string } => {
    const dates = rows
        .map((r) => r.invoiceDate?.slice(0, 10))   // keep only YYYY-MM-DD
        .filter((d): d is string => !!d && /^\d{4}-\d{2}-\d{2}$/.test(d));

    if (dates.length === 0) return { startDate: '', endDate: '' };
    dates.sort();
    return { startDate: dates[0], endDate: dates[dates.length - 1] };
};

export const DataIngestion: React.FC = () => {
    const [step, setStep] = useState<'upload' | 'processing' | 'results'>('upload');
    const [processingMessage, setProcessingMessage] = useState('Uploading and cleaning data...');
    const [activeTab, setActiveTab] = useState<'clean' | 'invalid' | 'rfm-scatter-results' | 'dirty'>('clean');

    // Pagination state
    const [page, setPage] = useState(0);
    const size = 15; // match default of backend

    // Data state
    const [cleanData, setCleanData] = useState<CleanRow[]>([]);
    const [cleanTotal, setCleanTotal] = useState(0);

    const [invalidData, setInvalidData] = useState<InvalidRow[]>([]);
    const [invalidTotal, setInvalidTotal] = useState(0);

    const [dirtyData, setDirtyData] = useState<DirtyRow[]>([]);
    const [dirtyTotal, setDirtyTotal] = useState(0);

    // RFM filter state - derived from clean data for now, but could be user inputs in the future
    const [rfmStartDate, setRfmStartDate] = useState('');
    const [rfmEndDate, setRfmEndDate] = useState('');
    const [rfmCountry, setRfmCountry] = useState('');

    // Fetch one cleaned-data page and return total rows for tab badges/empty checks.
    const fetchCleanPage = async (currentPage = 0): Promise<number> => {
        const cleanRes = await fetch(`/api/cleaning-data/cleaned?page=${currentPage}&size=${size}`);
        if (!cleanRes.ok) {
            throw new Error('Failed to fetch cleaned data');
        }
        const data = await cleanRes.json();
        setCleanData(data.entries);
        setCleanTotal(data.totalEntries ?? 0);

        // Derive RFM filter values from the full clean data on each fetch, since users can switch to the RFM tab at any time and we want the filters to reflect the current clean dataset
        const { startDate, endDate } = deriveDateRange(data.entries);
        if (startDate) setRfmStartDate(startDate);
        if (endDate) setRfmEndDate(endDate);
        // Use the most common country from this page as the default hint (optional)
        const countries = (data.entries as CleanRow[]).map((r) => r.country).filter((c): c is string => !!c);
        if (countries.length > 0) {
            const countryCounts: Record<string, number> = {};
            countries.forEach((c) => { countryCounts[c] = (countryCounts[c] || 0) + 1; });
            const mostCommonCountry = Object.entries(countryCounts).sort((a, b) => b[1] - a[1])[0][0];
            setRfmCountry(mostCommonCountry);
        }

        return data.totalEntries ?? 0;
    };

    // Only rejected records are considered "invalid" in the UI.
    const fetchInvalidPage = async (currentPage = 0): Promise<number> => {
        const invalidRes = await fetch(`/api/cleaning-data/manual-review?page=${currentPage}&size=${size}&reviewStatus=REJECTED`);
        if (!invalidRes.ok) {
            throw new Error('Failed to fetch invalid data');
        }
        const data = await invalidRes.json();
        setInvalidData(data.entries as InvalidRow[]);
        setInvalidTotal(data.totalEntries ?? 0);
        return data.totalEntries ?? 0;
    };

    // Raw rows now come from backend pagination, not local file parsing.
    const fetchDirtyPage = async (currentPage = 0): Promise<number> => {
        const dirtyRes = await fetch(`/api/cleaning-data/dirty?page=${currentPage}&size=${size}`);
        if (!dirtyRes.ok) {
            throw new Error('Failed to fetch dirty data');
        }
        const data = await dirtyRes.json();
        setDirtyData(data.entries as DirtyRow[]);
        setDirtyTotal(data.totalEntries ?? 0);
        return data.totalEntries ?? 0;
    };

    const fetchResults = async (
        currentPage = 0,
        mode: ResultsTab | 'all' = 'all'
    ): Promise<{ clean: number; invalid: number; dirty: number }> => {
        try {
            // On page changes, only fetch the active tab to reduce UI lag.
            if (mode === 'clean') {
                return {
                    clean: await fetchCleanPage(currentPage),
                    invalid: invalidTotal,
                    dirty: dirtyTotal
                };
            }

            if (mode === 'invalid') {
                return {
                    clean: cleanTotal,
                    invalid: await fetchInvalidPage(currentPage),
                    dirty: dirtyTotal
                };
            }

            if (mode === 'dirty') {
                return {
                    clean: cleanTotal,
                    invalid: invalidTotal,
                    dirty: await fetchDirtyPage(currentPage)
                };
            }

            // for the rfm scatter plot page
            if (mode === 'rfm-scatter-results') {
                // RFMScatterPlot handles its own fetch
                // onlyn need to ensure cleanData is loaded/updated
                const cleanCount = cleanData.length === 0 ? await fetchCleanPage(0) : cleanTotal;
                return { clean: cleanCount, invalid: invalidTotal, dirty: dirtyTotal };
            }

            // Full refresh is used only after job completion or "View Existing Results".
            const [cleanCount, invalidCount, dirtyCount] = await Promise.all([
                fetchCleanPage(currentPage),
                fetchInvalidPage(currentPage),
                fetchDirtyPage(currentPage)
            ]);

            return { clean: cleanCount, invalid: invalidCount, dirty: dirtyCount };
        } catch (e) {
            console.error("Failed to fetch results", e);
            throw e;
        }
    };

    const handlePageChange = (newPage: number) => {
        setPage(newPage);
        fetchResults(newPage, activeTab);
    };

    // Switching tabs triggers a fetch for that tab at the current page.
    const handleTabChange = (tab: ResultsTab) => {
        setActiveTab(tab);
        fetchResults(page, tab);
    };

    // Allows users to open previously processed DB-backed data without new upload.
    const handleViewExistingResults = async () => {
        try {
            setStep('processing');
            setProcessingMessage('Loading existing data...');
            setPage(0);

            const totals = await fetchResults(0, 'all');
            if (totals.clean === 0 && totals.invalid === 0 && totals.dirty === 0) {
                alert('No existing data found yet. Upload a file to create results.');
                setStep('upload');
                return;
            }

            setStep('results');
        } catch (error) {
            console.error('Load Existing Results Error:', error);
            alert('Could not load existing results. Please try again.');
            setStep('upload');
        }
    };

    const handleUploadSubmit = async (e: React.FormEvent) => {
        e.preventDefault();

        const fileInput = document.getElementById('fileUpload') as HTMLInputElement;
        const file = fileInput?.files?.[0];

        if (!file) {
            alert('Please select a file to upload first.');
            return;
        }

        setStep('processing');
        setProcessingMessage('Uploading file...');

        const formData = new FormData();
        formData.append('file', file);

        try {
            const uploadResponse = await fetch('/api/ingest/upload', {
                method: 'POST',
                body: formData
            });

            if (!uploadResponse.ok) {
                const errorMessage = await uploadResponse.text();
                alert('File Upload failed: ' + (errorMessage || uploadResponse.statusText));
                setStep('upload');
                return;
            }

            // Immediately trigger the cleaning job processing pipeline
            setProcessingMessage('Starting cleaning job...');
            const cleaningResponse = await fetch('/api/cleaning-jobs', {
                method: 'POST',
                headers: { 'Content-Type': 'application/json' },
                body: JSON.stringify({ batchSize: 2000 })
            });

            if (cleaningResponse.ok) {
                const jobData = await cleaningResponse.json();
                const jobId = jobData.jobId;
                const startedAt = Date.now();
                const maxPollingMs = 10 * 60 * 1000; // 10 minutes
                let consecutiveStatusErrors = 0;

                // Poll for completion
                const pollInterval = setInterval(async () => {
                    try {
                        if (Date.now() - startedAt > maxPollingMs) {
                            clearInterval(pollInterval);
                            alert('Cleaning is taking longer than expected. Please refresh and check results again in a moment.');
                            setStep('upload');
                            return;
                        }

                        const statusRes = await fetch(`/api/cleaning-jobs/${jobId}`);
                        if (!statusRes.ok) {
                            consecutiveStatusErrors++;
                            if (consecutiveStatusErrors >= 5) {
                                clearInterval(pollInterval);
                                alert('Failed to get cleaning job status repeatedly. Please try again.');
                                setStep('upload');
                            }
                            return;
                        }

                        consecutiveStatusErrors = 0;
                        const statusData = await statusRes.json();
                        const status = String(statusData.status ?? 'UNKNOWN');
                        setProcessingMessage(`Cleaning status: ${status}`);

                        if (status === 'COMPLETED') {
                            clearInterval(pollInterval);
                            setPage(0);
                            await fetchResults(0, 'all');
                            setStep('results');
                            return;
                        }

                        if (status === 'FAILED' || status === 'ERROR' || status === 'CANCELLED') {
                            clearInterval(pollInterval);
                            alert(`Cleaning job failed with status: ${status}`);
                            setStep('upload');
                        }
                    } catch (pollError) {
                        console.error('Polling Error:', pollError);
                        consecutiveStatusErrors++;
                        if (consecutiveStatusErrors >= 5) {
                            clearInterval(pollInterval);
                            alert('Cleaning job polling failed repeatedly. Please try again.');
                            setStep('upload');
                        }
                    }
                }, 1000); // Check every 1s
            } else {
                alert('Data cleaning trigger failed: ' + cleaningResponse.statusText);
                setStep('upload');
            }
        } catch (error) {
            console.error('Upload Error:', error);
            alert('Upload failed due to network error.');
            setStep('upload');
        }
    };

    const handleExport = () => {
        alert("Downloading CSV...\n(not actually downloading )");
    };

    return (
        <div style={{ padding: '20px', fontFamily: 'sans-serif' }}>

            {step === 'upload' && (
                <div>
                    <h2>File Upload (.csv or .xlsx)</h2>
                    <form onSubmit={handleUploadSubmit}>
                        <div>
                            <label htmlFor="fileUpload">Select Data File: </label>
                            <input type="file" id="fileUpload" accept=".csv, .xlsx" />
                        </div>
                        <br />
                        <button type="submit">Upload and Clean Data</button>
                    </form>
                    <br />
                    <button type="button" onClick={handleViewExistingResults}>View Existing Results</button>
                </div>
            )}

            {step === 'processing' && (
                <div>
                    <h2>Processing...</h2>
                    <p>{processingMessage}</p>
                </div>
            )}

            {step === 'results' && (
                <div>
                    <h2>Results</h2>
                    <br />
                    <button onClick={() => { setStep('upload'); setPage(0); }} style={{ marginBottom: '20px', padding: '5px 10px', backgroundColor: '#e0e0e0', border: '1px solid #777' }}>Start Over / Upload Another</button>

                    <div style={{ display: 'flex', gap: '8px' }}>
                        <button
                            onClick={() => handleTabChange('clean')}
                            style={{
                                fontWeight: activeTab === 'clean' ? 'bold' : 'normal',
                                backgroundColor: activeTab === 'clean' ? '#e0e0e0' : '#e0e0e0',
                                border: '1px solid #777',
                                borderBottom: activeTab === 'clean' ? 'none' : '1px solid #777',
                                padding: '5px 10px',
                                position: 'relative',
                                top: '1px',
                                zIndex: activeTab === 'clean' ? 1 : 0
                            }}
                        >
                            Successfully Cleaned Data ({cleanTotal})
                        </button>
                        <button
                            onClick={() => handleTabChange('invalid')}
                            style={{
                                fontWeight: activeTab === 'invalid' ? 'bold' : 'normal',
                                backgroundColor: activeTab === 'invalid' ? '#e0e0e0' : '#e0e0e0',
                                border: '1px solid #777',
                                borderBottom: activeTab === 'invalid' ? 'none' : '1px solid #777',
                                padding: '5px 10px',
                                position: 'relative',
                                top: '1px',
                                zIndex: activeTab === 'invalid' ? 1 : 0
                            }}
                        >
                            Invalid Items ({invalidTotal})
                        </button>
                        <button
                            onClick={() => handleTabChange('rfm-scatter-results')}
                            style={{
                                fontWeight: activeTab === 'rfm-scatter-results' ? 'bold' : 'normal',
                                backgroundColor: activeTab === 'rfm-scatter-results' ? '#e0e0e0' : '#e0e0e0',
                                border: '1px solid #777',
                                borderBottom: activeTab === 'rfm-scatter-results' ? 'none' : '1px solid #777',
                                padding: '5px 10px',
                                position: 'relative',
                                top: '1px',
                                zIndex: activeTab === 'rfm-scatter-results' ? 1 : 0
                            }}
                        >
                            RFM Scatter Plot
                        </button>
                        <button
                            onClick={() => handleTabChange('dirty')}
                            style={{
                                fontWeight: activeTab === 'dirty' ? 'bold' : 'normal',
                                backgroundColor: activeTab === 'dirty' ? '#e0e0e0' : '#e0e0e0',
                                border: '1px solid #777',
                                borderBottom: activeTab === 'dirty' ? 'none' : '1px solid #777',
                                padding: '5px 10px',
                                position: 'relative',
                                top: '1px',
                                zIndex: activeTab === 'dirty' ? 1 : 0
                            }}
                        >
                            Raw / Uncleaned Data ({dirtyTotal})
                        </button>
                    </div>

                    <div style={{ borderTop: '1px solid #777', borderBottom: '1px solid #777', padding: '10px 0', marginTop: '0px' }}>
                        {activeTab === 'clean' && (
                            <div style={{ padding: '0 10px' }}>
                                <div style={{ marginBottom: '10px', display: 'flex', justifyContent: 'space-between', alignItems: 'center' }}>
                                    <button onClick={handleExport} style={{ fontWeight: 'bold', padding: '5px 10px', backgroundColor: '#fff', border: '1px solid #777' }}>
                                        Download Cleaned Data (.CSV)
                                    </button>

                                    <div>
                                        <button onClick={() => handlePageChange(Math.max(0, page - 1))} disabled={page === 0}>Previous</button>
                                        <span style={{ margin: '0 10px' }}>Page {page + 1}</span>
                                        <button onClick={() => handlePageChange(page + 1)}>Next</button>
                                    </div>
                                </div>
                                <table style={{ borderCollapse: 'collapse', width: '100%' }}>
                                    <thead>
                                        <tr>
                                            <th style={{ textAlign: 'left', borderBottom: '1px solid #ccc', padding: '8px' }}>Invoice No</th>
                                            <th style={{ textAlign: 'left', borderBottom: '1px solid #ccc', padding: '8px' }}>Stock Code</th>
                                            <th style={{ textAlign: 'left', borderBottom: '1px solid #ccc', padding: '8px' }}>Description</th>
                                            <th style={{ textAlign: 'left', borderBottom: '1px solid #ccc', padding: '8px' }}>Quantity</th>
                                            <th style={{ textAlign: 'left', borderBottom: '1px solid #ccc', padding: '8px' }}>Unit Price</th>
                                            <th style={{ textAlign: 'left', borderBottom: '1px solid #ccc', padding: '8px' }}>Customer ID</th>
                                        </tr>
                                    </thead>
                                    <tbody>
                                        {cleanData.map((d, index) => (
                                            <tr key={`clean-${index}`}>
                                                <td style={{ padding: '8px' }}>{d.invoice}</td>
                                                <td style={{ padding: '8px' }}>{d.stockCode}</td>
                                                <td style={{ padding: '8px' }}>{d.description}</td>
                                                <td style={{ padding: '8px' }}>{d.quantity}</td>
                                                <td style={{ padding: '8px' }}>{d.price}</td>
                                                <td style={{ padding: '8px' }}>{d.customerId}</td>
                                            </tr>
                                        ))}
                                    </tbody>
                                </table>
                            </div>
                        )}

                        {activeTab === 'invalid' && (
                            <div style={{ padding: '0 10px' }}>
                                <div style={{ marginBottom: '10px', display: 'flex', justifyContent: 'space-between', alignItems: 'center' }}>
                                    <p style={{ marginTop: '10px', marginBottom: '10px', fontSize: '18px', fontWeight: 'bold', color: 'red' }}>Could not clean / ERROR</p>
                                    <div>
                                        <button onClick={() => handlePageChange(Math.max(0, page - 1))} disabled={page === 0}>Previous</button>
                                        <span style={{ margin: '0 10px' }}>Page {page + 1}</span>
                                        <button onClick={() => handlePageChange(page + 1)}>Next</button>
                                    </div>
                                </div>
                                <table style={{ borderCollapse: 'collapse', width: '100%' }}>
                                    <thead>
                                        <tr>
                                            <th style={{ textAlign: 'left', borderBottom: '1px solid #ccc', padding: '8px' }}>Invoice No</th>
                                            <th style={{ textAlign: 'left', borderBottom: '1px solid #ccc', padding: '8px' }}>Stock Code</th>
                                            <th style={{ textAlign: 'left', borderBottom: '1px solid #ccc', padding: '8px' }}>Description</th>
                                            <th style={{ textAlign: 'left', borderBottom: '1px solid #ccc', padding: '8px' }}>Quantity</th>
                                            <th style={{ textAlign: 'left', borderBottom: '1px solid #ccc', padding: '8px' }}>Unit Price</th>
                                            <th style={{ textAlign: 'left', borderBottom: '1px solid #ccc', padding: '8px' }}>Customer ID</th>
                                            <th style={{ textAlign: 'left', borderBottom: '1px solid #ccc', padding: '8px' }}>Issue / Reason</th>
                                        </tr>
                                    </thead>
                                    <tbody>
                                        {invalidData.map((d, index) => {
                                            const rawVals = parseRawValues(d.rawValues);
                                            return (
                                                <tr key={`invalid-${index}`}>
                                                    <td style={{ padding: '8px' }}>{rawVals.Invoice || 'N/A'}</td>
                                                    <td style={{ padding: '8px' }}>{rawVals.StockCode || 'N/A'}</td>
                                                    <td style={{ padding: '8px' }}>{rawVals.Description || 'N/A'}</td>
                                                    <td style={{ padding: '8px' }}>{rawVals.Quantity !== undefined ? rawVals.Quantity : 'N/A'}</td>
                                                    <td style={{ padding: '8px' }}>{rawVals.Price !== undefined ? rawVals.Price : 'N/A'}</td>
                                                    <td style={{ padding: '8px' }}>{rawVals.CustomerID !== undefined ? rawVals.CustomerID : 'N/A'}</td>
                                                    <td style={{ padding: '8px', color: 'red' }}><strong>{d.reviewStatus}: {d.reason} {d.validationErrors ? `(${d.validationErrors})` : ''}</strong></td>
                                                </tr>
                                            );
                                        })}
                                    </tbody>
                                </table>
                            </div>
                        )}

                        {/* RFM Scatter Plot*/}
                        {activeTab === 'rfm-scatter-results' && (
                            <div style={{ padding: '10px' }}>
                                {/*
                                  * The RFMScatterPlot component is fully self-contained:
                                  * it owns its own filter state and API calls.
                                  * We pass date-range hints derived from cleanData as
                                  * convenient pre-fills so the analyst doesn't have to
                                  * type them manually.
                                  */}
                                <RFMScatterPlot
                                    initialStartDate={rfmStartDate}
                                    initialEndDate={rfmEndDate}
                                    initialCountry={rfmCountry}
                                />
                            </div>
                        )}
 
                        {activeTab === 'dirty' && (
                            <div style={{ padding: '0 10px' }}>
                                <div style={{ marginBottom: '10px', display: 'flex', justifyContent: 'space-between', alignItems: 'center' }}>
                                    <p style={{ marginTop: '10px', marginBottom: '10px', fontSize: '18px', fontWeight: 'bold' }}>Uncleaned Raw Data</p>
                                    <div>
                                        <button onClick={() => handlePageChange(Math.max(0, page - 1))} disabled={page === 0}>Previous</button>
                                        <span style={{ margin: '0 10px' }}>Page {page + 1}</span>
                                        <button onClick={() => handlePageChange(page + 1)}>Next</button>
                                    </div>
                                </div>
                                <table style={{ borderCollapse: 'collapse', width: '100%' }}>
                                    <thead>
                                        <tr>
                                            <th style={{ textAlign: 'left', borderBottom: '1px solid #ccc', padding: '8px' }}>Invoice No</th>
                                            <th style={{ textAlign: 'left', borderBottom: '1px solid #ccc', padding: '8px' }}>Stock Code</th>
                                            <th style={{ textAlign: 'left', borderBottom: '1px solid #ccc', padding: '8px' }}>Description</th>
                                            <th style={{ textAlign: 'left', borderBottom: '1px solid #ccc', padding: '8px' }}>Quantity</th>
                                            <th style={{ textAlign: 'left', borderBottom: '1px solid #ccc', padding: '8px' }}>Unit Price</th>
                                            <th style={{ textAlign: 'left', borderBottom: '1px solid #ccc', padding: '8px' }}>Customer ID</th>
                                            <th style={{ textAlign: 'left', borderBottom: '1px solid #ccc', padding: '8px' }}>Date</th>
                                            <th style={{ textAlign: 'left', borderBottom: '1px solid #ccc', padding: '8px' }}>Country</th>
                                        </tr>
                                    </thead>
                                    <tbody>
                                        {dirtyData.map((d, index) => (
                                            <tr key={`dirty-${index}`}>
                                                <td style={{ padding: '8px' }}>{d.invoice || 'N/A'}</td>
                                                <td style={{ padding: '8px' }}>{d.stockCode || 'N/A'}</td>
                                                <td style={{ padding: '8px' }}>{d.description || 'N/A'}</td>
                                                <td style={{ padding: '8px' }}>{d.quantity || 'N/A'}</td>
                                                <td style={{ padding: '8px' }}>{d.price || 'N/A'}</td>
                                                <td style={{ padding: '8px' }}>{d.customerId || 'N/A'}</td>
                                                <td style={{ padding: '8px' }}>{d.invoiceDate || 'N/A'}</td>
                                                <td style={{ padding: '8px' }}>{d.country || 'N/A'}</td>
                                            </tr>
                                        ))}
                                    </tbody>
                                </table>
                            </div>
                        )}
                    </div>
                </div>
            )}
        </div>
    );
};
