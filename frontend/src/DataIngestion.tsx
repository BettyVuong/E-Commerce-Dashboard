import React, { useState } from 'react';
import Papa from 'papaparse';
import * as XLSX from 'xlsx';

export const DataIngestion: React.FC = () => {
    const [step, setStep] = useState<'upload' | 'processing' | 'results'>('upload');
    const [activeTab, setActiveTab] = useState<'clean' | 'invalid' | 'dirty'>('clean');

    // Pagination state
    const [page, setPage] = useState(0);
    const size = 15; // match default of backend

    // Data state
    const [cleanData, setCleanData] = useState<any[]>([]);
    const [cleanTotal, setCleanTotal] = useState(0);

    const [invalidData, setInvalidData] = useState<any[]>([]);
    const [invalidTotal, setInvalidTotal] = useState(0);

    const [dirtyData, setDirtyData] = useState<any[]>([]);
    const [dirtyTotal, setDirtyTotal] = useState(0);

    const fetchResults = async (currentPage = 0) => {
        try {
            const cleanRes = await fetch(`/api/cleaning-data/cleaned?page=${currentPage}&size=${size}`);
            if (cleanRes.ok) {
                const data = await cleanRes.json();
                setCleanData(data.entries);
                setCleanTotal(data.totalEntries);
            }

            const invalidRes = await fetch(`/api/cleaning-data/manual-review?page=${currentPage}&size=${size}`);
            if (invalidRes.ok) {
                const data = await invalidRes.json();

                // Filter out AUTO_CLEANED items client-side as requested
                const strictlyInvalid = data.entries.filter((entry: any) => entry.reviewStatus !== 'AUTO_CLEANED');
                setInvalidData(strictlyInvalid);

                // Note: Pagination total is slightly inaccurate now due to client-side filtering,
                // but this fixes the UI bug without touching the backend SQL query.
                setInvalidTotal(strictlyInvalid.length);
            }
        } catch (e) {
            console.error("Failed to fetch results", e);
        }
    };

    const handlePageChange = (newPage: number) => {
        setPage(newPage);
        fetchResults(newPage);
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

        // Parse file locally for the dirty data view
        const fileName = file.name.toLowerCase();
        if (fileName.endsWith('.csv')) {
            Papa.parse(file, {
                header: true,
                skipEmptyLines: true,
                complete: function (results) {
                    setDirtyData(results.data);
                    setDirtyTotal(results.data.length);
                }
            });
        } else if (fileName.endsWith('.xlsx')) {
            const reader = new FileReader();
            reader.onload = (evt) => {
                const bstr = evt.target?.result;
                const wb = XLSX.read(bstr, { type: 'binary' });
                const wsname = wb.SheetNames[0];
                const ws = wb.Sheets[wsname];
                const data = XLSX.utils.sheet_to_json(ws, { raw: false });
                setDirtyData(data);
                setDirtyTotal(data.length);
            };
            reader.readAsBinaryString(file);
        }

        const formData = new FormData();
        formData.append('file', file);

        try {
            const uploadResponse = await fetch('/api/ingest/upload', {
                method: 'POST',
                body: formData
            });

            if (!uploadResponse.ok) {
                alert('File Upload failed: ' + uploadResponse.statusText);
                setStep('upload');
                return;
            }

            // Immediately trigger the cleaning job processing pipeline
            const cleaningResponse = await fetch('/api/cleaning-jobs', {
                method: 'POST',
                headers: { 'Content-Type': 'application/json' },
                body: JSON.stringify({ batchSize: 100 })
            });

            if (cleaningResponse.ok) {
                const jobData = await cleaningResponse.json();
                const jobId = jobData.jobId;

                // Poll for completion
                const pollInterval = setInterval(async () => {
                    const statusRes = await fetch(`/api/cleaning-jobs/${jobId}`);
                    if (statusRes.ok) {
                        const statusData = await statusRes.json();
                        if (statusData.status === 'COMPLETED' || statusData.status === 'FAILED') {
                            clearInterval(pollInterval);
                            setPage(0);
                            await fetchResults(0);
                            setStep('results');
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
                </div>
            )}

            {step === 'processing' && (
                <div>
                    <h2>Processing...</h2>
                </div>
            )}

            {step === 'results' && (
                <div>
                    <h2>Results</h2>
                    <br />
                    <button onClick={() => { setStep('upload'); setPage(0); }} style={{ marginBottom: '20px', padding: '5px 10px', backgroundColor: '#e0e0e0', border: '1px solid #777' }}>Start Over / Upload Another</button>

                    <div style={{ display: 'flex', gap: '8px' }}>
                        <button
                            onClick={() => setActiveTab('clean')}
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
                            onClick={() => setActiveTab('invalid')}
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
                            onClick={() => setActiveTab('dirty')}
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
                                            const rawVals = d.rawValues ? JSON.parse(d.rawValues) : {};
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
                                        {dirtyData.slice(page * size, (page + 1) * size).map((d, index) => (
                                            <tr key={`dirty-${index}`}>
                                                <td style={{ padding: '8px' }}>{d.Invoice || d.invoice || 'N/A'}</td>
                                                <td style={{ padding: '8px' }}>{d.StockCode || d.stockCode || 'N/A'}</td>
                                                <td style={{ padding: '8px' }}>{d.Description || d.description || 'N/A'}</td>
                                                <td style={{ padding: '8px' }}>{d.Quantity || d.quantity || 'N/A'}</td>
                                                <td style={{ padding: '8px' }}>{d.Price || d.UnitPrice || d.price || d.unitPrice || 'N/A'}</td>
                                                <td style={{ padding: '8px' }}>{d.CustomerID || d.customerID || d.Customerid || 'N/A'}</td>
                                                <td style={{ padding: '8px' }}>{d.InvoiceDate || d.invoiceDate || 'N/A'}</td>
                                                <td style={{ padding: '8px' }}>{d.Country || d.country || 'N/A'}</td>
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
