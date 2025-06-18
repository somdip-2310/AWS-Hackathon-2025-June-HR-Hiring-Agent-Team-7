// UNIFIED main.js - Complete HR Agent JavaScript

// ========================================
// GLOBAL VARIABLES
// ========================================
let currentJobs = [];
let currentCandidates = [];
let uploadedInSession = false;
let currentSessionId = null;
let sessionTimer = null;
let sessionTimerInterval = null;
let cleanupCheckInterval = null;
let userEmail = null;
let userQueueId = null;
let queueCheckInterval = null;
let uploadSessionActive = false;
let hasUploadedInCurrentSession = false;

// ========================================
// INITIALIZATION
// ========================================
// Update the DOMContentLoaded event listener to check for token
document.addEventListener('DOMContentLoaded', function() {
    console.log('HR Agent initializing...');
    
    // Check for access token first
    checkForAccessToken();
    
    // Initialize session management first
    SessionManager.init();
    
    // Always enable reading sections first
    SessionManager.enableReadingSections();
    
    // Initialize all components
    loadJobs();
    loadCandidates();
    updateStatistics();
    updateButtonStates();
    
    // Set up event listeners
    const jobSelect = document.getElementById('jobSelect');
    if (jobSelect) {
        jobSelect.addEventListener('change', updateButtonStates);
    }
    
    console.log('Initialization complete');
});

// ========================================
// JOB MANAGEMENT
// ========================================
async function loadJobs() {
    try {
        console.log('Loading jobs...');
        const response = await fetch('/api/jobs');
        
        if (response.ok) {
            currentJobs = await response.json();
            console.log('Jobs loaded:', currentJobs.length);
            populateJobSelect();
            updateStatistics();
        } else {
            console.warn('Jobs API failed, using sample data');
            initializeSampleJobs();
            populateJobSelect();
        }
    } catch (error) {
        console.error('Error loading jobs:', error);
        initializeSampleJobs();
        populateJobSelect();
    }
}

function initializeSampleJobs() {
    currentJobs = [
        { id: "1", title: "Junior Frontend Developer", experienceLevel: "ENTRY" },
        { id: "2", title: "Data Analyst - Tech & Analytics", experienceLevel: "MID" },
        { id: "3", title: "Senior Full Stack Developer", experienceLevel: "SENIOR" },
        { id: "4", title: "Java Backend Developer", experienceLevel: "MID" },
        { id: "5", title: "DevOps Engineer - Cloud Infrastructure", experienceLevel: "MID" },
        { id: "6", title: "Full Stack Developer - React/Django", experienceLevel: "MID" },
        { id: "7", title: "Marketing Data Analyst", experienceLevel: "MID" },
        { id: "8", title: "Senior Marketing Manager - Digital", experienceLevel: "SENIOR" },
        { id: "9", title: "Demand Planning Manager - Supply Chain", experienceLevel: "SENIOR" },
        { id: "10", title: "Senior Angular Developer", experienceLevel: "MID" }
    ];
}

function populateJobSelect() {
    const jobSelect = document.getElementById('jobSelect');
    if (!jobSelect) return;
    
    jobSelect.innerHTML = '<option value="">Choose a job position...</option>';
    
    currentJobs.forEach(job => {
        const option = document.createElement('option');
        option.value = job.id;
        option.textContent = `${job.title} (${job.experienceLevel})`;
        jobSelect.appendChild(option);
    });
    
    console.log('Job select populated with', currentJobs.length, 'jobs');
}

// ========================================
// CANDIDATE MANAGEMENT - FIXED
// ========================================
async function loadCandidates() {
    try {
        console.log('Loading candidates from API...');
        const response = await fetch('/api/candidates');
        
        if (response.ok) {
            const candidatesData = await response.json();
            console.log('Raw API response:', candidatesData);
            
            // CRITICAL FIX: Ensure proper assignment
            currentCandidates = Array.isArray(candidatesData) ? candidatesData : [];
            
            console.log('Candidates assigned to currentCandidates:', currentCandidates.length);
            console.log('Current candidates:', currentCandidates);
            
            // Force refresh of display
            displayCandidates();
            updateStatistics();
            updateButtonStates();
            
        } else {
            console.error('Failed to load candidates, status:', response.status);
            currentCandidates = [];
            displayCandidates();
        }
    } catch (error) {
        console.error('Error loading candidates:', error);
        currentCandidates = [];
        displayCandidates();
    }
}

function displayCandidates() {
    const candidatesList = document.getElementById('candidatesList');
    if (!candidatesList) {
        console.error('candidatesList element not found');
        return;
    }
    
    console.log('Displaying candidates. Count:', currentCandidates ? currentCandidates.length : 0);
    console.log('Candidates data:', currentCandidates);

    if (!currentCandidates || currentCandidates.length === 0) {
        candidatesList.innerHTML = `
            <div class="text-center py-12">
                <i class="fas fa-inbox text-6xl text-gray-300 mb-4"></i>
                <p class="text-gray-500">No candidates uploaded yet</p>
                <p class="text-gray-400 text-sm">Upload resume files to see extracted skills here</p>
            </div>
        `;
        return;
    }

    candidatesList.innerHTML = '';

    currentCandidates.forEach((candidate, index) => {
        const candidateDiv = document.createElement('div');
        candidateDiv.className = 'border border-gray-200 rounded-lg p-6 hover:shadow-lg transition-all duration-300 hover:border-purple-300 fade-in bg-white';
        candidateDiv.style.animationDelay = `${index * 0.05}s`;
        
        // Format process date
        const processDate = candidate.processedAt ? new Date(candidate.processedAt).toLocaleDateString() : 'Recently';
        
        candidateDiv.innerHTML = `
            <div class="flex justify-between items-start">
                <div class="flex-1">
                    <div class="flex items-center mb-4">
                        <div class="w-10 h-10 bg-purple-100 rounded-full flex items-center justify-center mr-3">
                            <i class="fas fa-user text-purple-600"></i>
                        </div>
                        <h3 class="text-lg font-semibold text-gray-800">
                            ${candidate.fileName || 'Unknown Candidate'}
                        </h3>
                    </div>
                    <div class="grid grid-cols-1 md:grid-cols-2 gap-4">
                        <div>
                            <p class="text-sm font-semibold text-gray-700 mb-2">
                                <i class="fas fa-tools mr-1 text-blue-500"></i>
                                Technical Skills:
                            </p>
                            <div class="flex flex-wrap gap-2">
                                ${candidate.technicalSkills && candidate.technicalSkills.length > 0 ? 
                                    candidate.technicalSkills.map(skill =>
                                        `<span class="bg-blue-100 text-blue-800 text-xs px-3 py-1 rounded-full font-medium">${skill}</span>`
                                    ).join('') : 
                                    '<span class="text-gray-500 text-sm">No skills extracted</span>'
                                }
                            </div>
                        </div>
                        <div>
                            <p class="text-sm font-semibold text-gray-700 mb-2">
                                <i class="fas fa-layer-group mr-1 text-green-500"></i>
                                Experience Level:
                            </p>
                            <span class="inline-block px-4 py-2 rounded-full text-sm font-medium ${
                                candidate.experienceLevel === 'SENIOR' ? 'bg-purple-100 text-purple-800' :
                                candidate.experienceLevel === 'MID' ? 'bg-green-100 text-green-800' :
                                candidate.experienceLevel === 'ENTRY' ? 'bg-yellow-100 text-yellow-800' :
                                'bg-gray-100 text-gray-800'
                            }">
                                <i class="fas fa-star mr-1"></i>
                                ${candidate.experienceLevel || 'Not specified'}
                            </span>
                        </div>
                    </div>
                    ${candidate.softSkills && candidate.softSkills.length > 0 ? `
                        <div class="mt-4">
                            <p class="text-sm font-semibold text-gray-700 mb-2">
                                <i class="fas fa-users mr-1 text-green-500"></i>
                                Soft Skills:
                            </p>
                            <div class="flex flex-wrap gap-2">
                                ${candidate.softSkills.map(skill =>
                                    `<span class="bg-green-100 text-green-800 text-xs px-3 py-1 rounded-full font-medium">${skill}</span>`
                                ).join('')}
                            </div>
                        </div>
                    ` : ''}
                </div>
                <div class="text-right ml-4">
                    <p class="text-sm text-gray-500">
                        <i class="far fa-clock mr-1"></i>
                        ${processDate}
                    </p>
                    <button onclick="deleteCandidate('${candidate.id}')" 
                            class="mt-2 text-red-600 hover:text-red-800 text-sm transition-colors">
                        <i class="fas fa-trash"></i>
                    </button>
                </div>
            </div>
        `;

        candidatesList.appendChild(candidateDiv);
    });
    
    console.log('Candidates displayed successfully');
}

// ========================================
// UPLOAD HANDLING - FIXED
// ========================================
function handleFileSelect() {
    if (!currentSessionId) {
        alert('Please start a session first');
        SessionManager.showModal();
        document.getElementById('resumeFiles').value = '';
        return;
    }
    
    // Check if upload is already in progress
    if (uploadSessionActive) {
        alert('Please wait for current upload to complete before selecting new files');
        document.getElementById('resumeFiles').value = '';
        return;
    }
    
    const files = document.getElementById('resumeFiles').files;
    if (files.length > 0) {
        console.log('Files selected:', files.length);
        uploadFiles(Array.from(files));
    }
}

function createFileProgressItem(fileName, fileId) {
    const progressItem = document.createElement('div');
    progressItem.id = `progress-${fileId}`;
    progressItem.className = 'upload-progress-item bg-gray-50 rounded-lg p-4 fade-in';
    progressItem.innerHTML = `
        <div class="flex items-center justify-between mb-2">
            <div class="flex items-center">
                <i class="fas fa-file-alt mr-3 text-purple-600"></i>
                <span class="font-medium text-gray-700">${fileName}</span>
            </div>
            <span class="status-icon">
                <i class="fas fa-spinner fa-spin text-purple-600"></i>
            </span>
        </div>
        <div class="bg-gray-200 rounded-full h-2 overflow-hidden">
            <div class="progress-bar bg-gradient-to-r from-purple-500 to-purple-700 h-2 rounded-full transition-all duration-500" style="width: 0%"></div>
        </div>
        <p class="status-text text-sm text-gray-600 mt-2">Uploading...</p>
    `;
    return progressItem;
}

async function uploadFiles(files) {
    console.log('Starting upload of', files.length, 'files');
	console.log('Starting upload of', files.length, 'files');
	    
	    // Immediately freeze upload functionality
	    freezeUploadSection();
	    uploadSessionActive = true;
    const progressSection = document.getElementById('uploadProgressSection');
    const fileProgressList = document.getElementById('fileProgressList');
    const resultsDiv = document.getElementById('uploadResults');

    if (progressSection) progressSection.classList.remove('hidden');
    if (fileProgressList) fileProgressList.innerHTML = '';
    if (resultsDiv) resultsDiv.innerHTML = '';

    let successCount = 0;
    let totalFiles = files.length;

    // Create progress items for all files
    files.forEach((file, index) => {
        const progressItem = createFileProgressItem(file.name, index);
        if (fileProgressList) fileProgressList.appendChild(progressItem);
    });

    // Upload files one by one
    for (let i = 0; i < files.length; i++) {
        const file = files[i];
        const formData = new FormData();
        formData.append('file', file);
        
        if (currentSessionId) {
            formData.append('sessionId', currentSessionId);
        }
        
        const progressItem = document.getElementById(`progress-${i}`);
        const progressBar = progressItem?.querySelector('.progress-bar');
        const statusText = progressItem?.querySelector('.status-text');
        const statusIcon = progressItem?.querySelector('.status-icon');

        try {
            console.log(`Uploading file ${i + 1}/${files.length}:`, file.name);
            console.log('Current sessionId:', currentSessionId); // Debug log
            
            // Update progress
            if (progressBar) progressBar.style.width = '30%';
            if (statusText) statusText.textContent = 'Uploading to server...';

            const response = await fetch('/api/upload', {
                method: 'POST',
                body: formData
            });

            if (progressBar) progressBar.style.width = '60%';
            if (statusText) statusText.textContent = 'Extracting text and analyzing skills...';

            if (!response.ok) {
                throw new Error(`HTTP error! status: ${response.status}`);
            }

            const result = await response.json();
            console.log('Upload result for', file.name, ':', result);

            // Check for success
            if (result.success && (result.candidateId || result.id)) {
                successCount++;
                if (progressBar) progressBar.style.width = '100%';
                
                // Count skills from result
                const skillsCount = result.extractedData?.skillsCount || 0;
                
                if (statusText) {
                    statusText.textContent = `✅ Completed! ${skillsCount} skills extracted.`;
                    statusText.classList.remove('text-gray-600');
                    statusText.classList.add('text-green-600', 'font-semibold');
                }
                if (statusIcon) {
                    statusIcon.innerHTML = '<i class="fas fa-check-circle text-green-500 text-lg"></i>';
                }
                if (progressItem) {
                    progressItem.classList.add('border', 'border-green-200', 'bg-green-50');
                }
                
                // Mark step 1 as completed
                const step1 = document.getElementById('step1');
                if (step1) step1.classList.add('completed');
                
            } else {
                throw new Error(result.error || 'Upload failed');
            }

        } catch (error) {
            console.error('Upload error for', file.name, ':', error);
            
            if (progressBar) {
                progressBar.style.width = '100%';
                progressBar.classList.remove('from-purple-500', 'to-purple-700');
                progressBar.classList.add('bg-red-500');
            }
            if (statusText) {
                statusText.textContent = `❌ Failed: ${error.message}`;
                statusText.classList.remove('text-gray-600');
                statusText.classList.add('text-red-600', 'font-semibold');
            }
            if (statusIcon) {
                statusIcon.innerHTML = '<i class="fas fa-exclamation-circle text-red-500 text-lg"></i>';
            }
            if (progressItem) {
                progressItem.classList.add('border', 'border-red-200', 'bg-red-50');
            }
        }
    }

    // Show summary
    if (successCount > 0) {
        uploadedInSession = true;
		hasUploadedInCurrentSession = true;
		        
		        // Keep upload section frozen
		        maintainUploadFreeze();
        const summaryDiv = document.createElement('div');
        summaryDiv.className = 'mt-6 p-6 bg-gradient-to-r from-green-50 to-green-100 rounded-xl border-2 border-green-200 fade-in shadow-lg';
        summaryDiv.innerHTML = `
            <div class="flex items-center justify-between">
                <div class="flex items-center">
                    <div class="w-12 h-12 bg-green-500 rounded-full flex items-center justify-center mr-4">
                        <i class="fas fa-check text-white text-xl"></i>
                    </div>
                    <div>
                        <h3 class="text-lg font-bold text-green-800 mb-1">Upload Complete!</h3>
                        <p class="text-green-700">${successCount} of ${totalFiles} resumes processed successfully</p>
                    </div>
                </div>
                <div class="text-right">
                    <div class="bg-white px-4 py-2 rounded-lg border border-green-300">
                        <span class="text-green-600 font-bold text-lg">
                            <i class="fas fa-arrow-down mr-1"></i>
                            Ready for matching!
                        </span>
                    </div>
                </div>
            </div>
        `;
        if (resultsDiv) resultsDiv.appendChild(summaryDiv);
        
        // CRITICAL FIX: Force reload candidates with delay
        console.log('Upload complete, reloading candidates...');
        setTimeout(async () => {
            await loadCandidates();
            console.log('Candidates reloaded after upload');
			// Auto-scroll to job selection section
			            scrollToJobSelection();
        }, 2000); // 2 second delay to ensure backend processing is complete
    }
    
    // Clear file input
    const resumeFiles = document.getElementById('resumeFiles');
    if (resumeFiles) resumeFiles.value = '';
}

// ========================================
// MATCHING FUNCTIONS
// ========================================
async function findMatches() {
    const jobSelect = document.getElementById('jobSelect');
    const jobId = jobSelect?.value;
    
    if (!jobId) {
        alert('Please select a job position');
        return;
    }

    if (!currentCandidates || currentCandidates.length === 0) {
        alert('Please upload some resumes first.');
        return;
    }

    console.log('Finding matches for job ID:', jobId);

    // Show loading state
    const matchingLoader = document.getElementById('matchingLoader');
    const matchResults = document.getElementById('matchResults');
    const findMatchesBtn = document.getElementById('findMatchesBtn');
    
    if (matchingLoader) matchingLoader.classList.remove('hidden');
    if (matchResults) matchResults.classList.add('hidden');
    if (findMatchesBtn) findMatchesBtn.disabled = true;
    
    // Animate progress
    const progressBar = document.getElementById('matchingProgress');
    const statusText = document.getElementById('matchingStatus');
    
    if (progressBar) progressBar.style.width = '20%';
    if (statusText) statusText.textContent = 'Evaluating skills and experience...';
    
    setTimeout(() => {
        if (progressBar) progressBar.style.width = '50%';
        if (statusText) statusText.textContent = 'Analyzing candidate compatibility...';
    }, 1000);
    
    setTimeout(() => {
        if (progressBar) progressBar.style.width = '80%';
        if (statusText) statusText.textContent = 'Calculating match scores...';
    }, 2000);

    try {
        // Use POST method as expected by your controller
        const response = await fetch('/api/match', {
            method: 'POST',
            headers: {
                'Content-Type': 'application/x-www-form-urlencoded',
            },
            body: `jobId=${encodeURIComponent(jobId)}`
        });
        
        if (!response.ok) {
            throw new Error(`HTTP error! status: ${response.status}`);
        }
        
        const result = await response.json();
        console.log('Match result:', result);
        
        // Extract matches from result
        const matches = result.matches || [];
        
        // Complete progress
        if (progressBar) progressBar.style.width = '100%';
        if (statusText) statusText.textContent = 'Matching complete!';
        
        setTimeout(() => {
            displayMatches(matches);
            if (matchingLoader) matchingLoader.classList.add('hidden');
            if (matchResults) matchResults.classList.remove('hidden');
            if (findMatchesBtn) findMatchesBtn.disabled = false;
            updateButtonStates();
            
            // Update match count
            const matchCount = document.getElementById('matchCount');
            if (matchCount) matchCount.textContent = matches.length;
            
            // Mark step 2 as completed
            const step2 = document.getElementById('step2');
            if (step2) step2.classList.add('completed');
            
            // Scroll to results
            if (matchResults) {
                matchResults.scrollIntoView({ behavior: 'smooth', block: 'start' });
            }
        }, 500);
        
    } catch (error) {
        console.error('Error finding matches:', error);
        alert('Failed to find matches: ' + error.message);
        if (matchingLoader) matchingLoader.classList.add('hidden');
        if (findMatchesBtn) findMatchesBtn.disabled = false;
        updateButtonStates();
    }
}

function displayMatches(matches) {
    const matchList = document.getElementById('matchList');
    if (!matchList) return;
    
    if (!matches || matches.length === 0) {
        matchList.innerHTML = `
            <div class="text-center py-8">
                <i class="fas fa-search text-6xl text-gray-300 mb-4"></i>
                <p class="text-gray-500">No matches found for this position</p>
                <p class="text-gray-400 text-sm">Try uploading more resumes or selecting a different position</p>
            </div>
        `;
        return;
    }
    
    matchList.innerHTML = matches.map((match, index) => {
        const score = Math.round(match.score || 0);
        const scoreClass = score >= 80 ? 'text-green-600' : score >= 60 ? 'text-yellow-600' : 'text-red-600';
        const bgColor = score >= 80 ? 'bg-green-50 border-green-500' :
                       score >= 60 ? 'bg-yellow-50 border-yellow-500' : 
                       'bg-red-50 border-red-500';
        const rankIcon = index === 0 ? 'fa-trophy text-yellow-500' : 
                        index === 1 ? 'fa-medal text-gray-400' : 
                        index === 2 ? 'fa-award text-orange-600' : 
                        'fa-user text-gray-400';
        
        return `
            <div class="border-l-4 ${bgColor} p-6 mb-4 rounded-r-lg hover:shadow-md transition-shadow fade-in"
                 style="animation-delay: ${index * 0.1}s">
                <div class="flex justify-between items-center">
                    <div class="flex items-center flex-1">
                        <div class="w-12 h-12 bg-white rounded-full flex items-center justify-center mr-4 shadow">
                            <i class="fas ${rankIcon} text-xl"></i>
                        </div>
                        <div>
                            <h3 class="text-xl font-semibold text-gray-800">
                                ${match.candidateName || 'Unknown Candidate'}
                            </h3>
                            <p class="text-gray-600 text-sm">Rank #${index + 1}</p>
                        </div>
                    </div>
                    <div class="text-center ml-6">
                        <div class="text-4xl font-bold ${scoreClass}">
                            ${score}%
                        </div>
                        <div class="text-sm text-gray-500">Match Score</div>
                        <div class="mt-2">
                            ${score >= 80 ? '<span class="text-xs bg-green-200 text-green-800 px-3 py-1 rounded-full font-medium">Excellent Match</span>' :
                              score >= 60 ? '<span class="text-xs bg-yellow-200 text-yellow-800 px-3 py-1 rounded-full font-medium">Good Match</span>' :
                              '<span class="text-xs bg-red-200 text-red-800 px-3 py-1 rounded-full font-medium">Fair Match</span>'}
                        </div>
                    </div>
                </div>
            </div>
        `;
    }).join('');
}

// ========================================
// UTILITY FUNCTIONS
// ========================================
function updateStatistics() {
    const candidateCount = document.getElementById('candidateCount');
    const jobCount = document.getElementById('jobCount');
    
    if (candidateCount) {
        candidateCount.textContent = currentCandidates ? currentCandidates.length : 0;
    }
    if (jobCount) {
        jobCount.textContent = currentJobs ? currentJobs.length : 0;
    }
}

function updateButtonStates() {
    const jobSelect = document.getElementById('jobSelect');
    const findMatchesBtn = document.getElementById('findMatchesBtn');
    
    if (!jobSelect || !findMatchesBtn) return;
    
    const jobSelected = jobSelect.value !== '';
    const candidatesExist = (currentCandidates && currentCandidates.length > 0) || uploadedInSession;
    
    if (jobSelected && candidatesExist) {
        findMatchesBtn.disabled = false;
        findMatchesBtn.className = 'w-full bg-purple-600 hover:bg-purple-700 text-white font-bold py-3 px-6 rounded-lg transition duration-300';
    } else {
        findMatchesBtn.disabled = true;
        findMatchesBtn.className = 'w-full bg-gray-400 text-white font-bold py-3 px-6 rounded-lg transition duration-300 disabled:cursor-not-allowed';
    }
}

// ========================================
// ADDITIONAL FUNCTIONS
// ========================================
async function deleteCandidate(candidateId) {
    if (!confirm('Are you sure you want to delete this candidate?')) {
        return;
    }
    
    try {
        const response = await fetch(`/api/candidates/${candidateId}`, {
            method: 'DELETE'
        });
        
        if (response.ok) {
            await loadCandidates();
        } else {
            alert('Failed to delete candidate. Please try again.');
        }
    } catch (error) {
        console.error('Error deleting candidate:', error);
        alert('Failed to delete candidate. Please try again.');
    }
}

async function clearAllCandidates() {
    if (!confirm('Are you sure you want to delete all candidates? This action cannot be undone.')) {
        return;
    }
    
    try {
        const response = await fetch('/api/candidates/all', {
            method: 'DELETE'
        });
        
        if (response.ok) {
            currentCandidates = [];
            displayCandidates();
            updateStatistics();
            uploadedInSession = false;
			// Reset upload state
			            resetUploadState();
            updateButtonStates();
            
            // Clear match results
            const matchResults = document.getElementById('matchResults');
            if (matchResults) matchResults.classList.add('hidden');
            
            const matchCount = document.getElementById('matchCount');
            if (matchCount) matchCount.textContent = '0';
            
            // Reset step indicators
            const step1 = document.getElementById('step1');
            const step2 = document.getElementById('step2');
            if (step1) step1.classList.remove('completed');
            if (step2) step2.classList.remove('completed');
        } else {
            alert('Failed to clear candidates. Please try again.');
        }
    } catch (error) {
        console.error('Error clearing candidates:', error);
        alert('Failed to clear candidates. Please try again.');
    }
}

async function downloadSampleResumes() {
    try {
        const btn = event.target.closest('button');
        const originalContent = btn.innerHTML;
        btn.innerHTML = '<i class="fas fa-spinner fa-spin mr-2"></i>Preparing download...';
        btn.disabled = true;
        
        let response = await fetch('/api/sample-resumes/download');
        
        if (!response.ok) {
            response = await fetch('/sample-resumes/sample-resumes.zip');
            if (!response.ok) {
                throw new Error('Sample resumes file not found');
            }
        }
        
        const blob = await response.blob();
        const url = window.URL.createObjectURL(blob);
        const link = document.createElement('a');
        link.href = url;
        link.download = 'HR-Agent-Sample-Resumes.zip';
        document.body.appendChild(link);
        link.click();
        document.body.removeChild(link);
        window.URL.revokeObjectURL(url);
        
        btn.innerHTML = originalContent;
        btn.disabled = false;
        
        setTimeout(() => {
            alert('Sample resumes downloaded! Please unarchive the zip and upload using the Browse Files button.');
        }, 1000);
        
    } catch (error) {
        console.error('Error downloading sample resumes:', error);
        alert('Sample resumes file not found. Please ensure the sample-resumes.zip file is available.');
        
        const btn = event.target.closest('button');
        if (btn) {
            btn.innerHTML = '<i class="fas fa-download mr-2"></i>Download Test Data';
            btn.disabled = false;
        }
    }
}

function downloadResults() {
    const matchList = document.getElementById('matchList');
    if (!matchList) return;
    
    const content = matchList.innerText;
    const blob = new Blob([content], { type: 'text/plain' });
    const url = window.URL.createObjectURL(blob);
    const a = document.createElement('a');
    a.href = url;
    a.download = 'match_results.txt';
    document.body.appendChild(a);
    a.click();
    document.body.removeChild(a);
    window.URL.revokeObjectURL(url);
}

function clearResults() {
    const matchResults = document.getElementById('matchResults');
    const matchList = document.getElementById('matchList');
    const matchCount = document.getElementById('matchCount');
    
    if (matchResults) matchResults.classList.add('hidden');
    if (matchList) matchList.innerHTML = '';
    if (matchCount) matchCount.textContent = '0';
}

// ========================================
// SESSION MANAGEMENT
// ========================================
// Replace the entire SessionManager object in main.js with this consolidated fix

const SessionManager = {
    // Add state tracking
    modalState: {
        isVisible: false,
        currentStep: null,
        isUserInteracting: false,
        lastInteraction: null
    },
	queueTimeouts: {
	    userTurnTimeout: null,
	    userTurnStartTime: null,
	    maxWaitTime: 120000, // 2 minutes to claim your turn
	},
    init() {
        this.setupEventListeners();
        this.checkInitialSessionStatus();
		this.requestNotificationPermission(); 
        // Only start cleanup monitoring if user has an active session
        if (currentSessionId) {
            this.startCleanupMonitoring();
        }
    },

    // Track user interactions to prevent unwanted modal hiding
    trackInteraction() {
        this.modalState.isUserInteracting = true;
        this.modalState.lastInteraction = Date.now();
        
        // Reset interaction flag after 2 seconds of inactivity
        clearTimeout(this.interactionTimeout);
        this.interactionTimeout = setTimeout(() => {
            this.modalState.isUserInteracting = false;
        }, 2000);
    },

    showArchitectureInfo() {
        this.trackInteraction();
        // Hide all current steps
        ['emailStep', 'verificationStep', 'sessionActiveStep', 'successStep', 'errorStep'].forEach(id => {
            const element = document.getElementById(id);
            if (element) {
                element.classList.add('hidden');
            }
        });
        
        // Show architecture info step
        const architectureStep = document.getElementById('architectureInfoStep');
        if (architectureStep) {
            architectureStep.classList.remove('hidden');
        }
    },

    hideArchitectureInfo() {
        this.trackInteraction();
        document.getElementById('architectureInfoStep').classList.add('hidden');
        this.showStep('email');
    },
    
    setupEventListeners() {
        // Track interactions on all input fields
        ['emailInput', 'codeInput'].forEach(id => {
            const element = document.getElementById(id);
            if (element) {
                element.addEventListener('focus', () => this.trackInteraction());
                element.addEventListener('input', () => this.trackInteraction());
            }
        });

        // Track modal clicks
        const modal = document.getElementById('sessionModal');
        if (modal) {
            modal.addEventListener('click', () => this.trackInteraction());
        }

        // Email form submission
        document.getElementById('emailForm').addEventListener('submit', (e) => {
            e.preventDefault();
            this.trackInteraction();
            this.requestVerificationCode();
        });
        
        // Verification form submission
        document.getElementById('verificationForm').addEventListener('submit', (e) => {
            e.preventDefault();
            this.trackInteraction();
            this.verifyCode();
        });
        
        // Navigation buttons
        document.getElementById('backToEmailBtn').addEventListener('click', () => {
            this.trackInteraction();
            this.showStep('email');
        });
        
        document.getElementById('resendCodeBtn').addEventListener('click', () => {
            this.trackInteraction();
            this.requestVerificationCode();
        });
        
        document.getElementById('refreshStatusBtn').addEventListener('click', () => {
            this.trackInteraction();
            this.checkSessionStatus();
        });
        
        document.getElementById('startDemoBtn').addEventListener('click', () => {
            this.trackInteraction();
            this.startDemo();
        });
        
        document.getElementById('tryAgainBtn').addEventListener('click', () => {
            this.trackInteraction();
            this.showStep('email');
        });
        
        document.getElementById('endSessionBtn').addEventListener('click', () => {
            this.trackInteraction();
            this.endSession();
        });
        
        // Auto-check session status - ONLY when appropriate
        setInterval(() => {
            // Don't check if user is actively interacting with modal
            if (this.modalState.isUserInteracting) {
                console.log('User is interacting, skipping session check');
                return;
            }
            
            // Don't check if modal is showing email/verification steps
            if (this.modalState.isVisible && 
                (this.modalState.currentStep === 'email' || 
                 this.modalState.currentStep === 'verification' ||
                 this.modalState.currentStep === 'architecture')) {
                console.log('Modal is in input state, skipping session check');
                return;
            }
            
            if (currentSessionId) {
                this.validateCurrentSession();
            } else if (this.modalState.currentStep === 'sessionActive') {
                // Only check if we're showing the waiting screen
                this.checkSessionStatus();
            }
        }, 30000);
    },
    
    startCleanupMonitoring() {
        // Clear any existing interval
        if (cleanupCheckInterval) {
            clearInterval(cleanupCheckInterval);
        }
        
        // Only monitor cleanup when there's an active session
        cleanupCheckInterval = setInterval(async () => {
            // Skip if user is interacting
            if (this.modalState.isUserInteracting) {
                return;
            }
            
            try {
                const response = await fetch('/api/session/cleanup-check');
                const data = await response.json();
                
                if (data.sessionExpired && data.dataCleanupRequired) {
                    this.handleDataCleanup(data);
                }
            } catch (error) {
                console.log('Cleanup check failed:', error);
            }
        }, 15000);
    },

    // FIX 2: Modified joinQueue to show email verification page
    joinQueue() {
        if (!userEmail) {
            // Instead of just showing alert, navigate to email verification
            console.log('No email found, redirecting to email verification');
            
            // Show the modal if hidden
            this.showModal();
            
            // Navigate to email step
            this.showStep('email');
            
            // Focus on email input
            setTimeout(() => {
                const emailInput = document.getElementById('emailInput');
                if (emailInput) {
                    emailInput.focus();
                }
            }, 100);
            
            return;
        }
        
        fetch('/api/session/join-queue', {
            method: 'POST',
            headers: { 'Content-Type': 'application/x-www-form-urlencoded' },
            body: `email=${encodeURIComponent(userEmail)}`
        })
        .then(response => response.json())
        .then(data => {
            if (data.success) {
                userQueueId = data.queueId;
                alert(`You've been added to the queue at position #${data.position}`);
                this.startQueueMonitoring();
            } else {
                alert(data.message || 'Failed to join queue');
            }
        })
        .catch(error => {
            console.error('Error joining queue:', error);
            alert('Failed to join queue');
        });
    },

    startQueueMonitoring() {
        // Clear existing interval
        if (queueCheckInterval) {
            clearInterval(queueCheckInterval);
        }
        
        queueCheckInterval = setInterval(() => {
            // Don't check if user is interacting
            if (this.modalState.isUserInteracting) {
                return;
            }
            this.checkQueueStatus();
        }, 10000);
        
        // Check immediately
        this.checkQueueStatus();
    },

	async checkQueueStatus() {
	        try {
	            const response = await fetch('/api/session/queue-status');
	            const data = await response.json();
	            
	            if (data.success) {
	                this.updateQueueDisplay(data);
	                
	                // Check if it's this user's turn
	                if (data.isYourTurn && userEmail) {
	                    this.handleYourTurn();
	                }
	                
	                // Check if someone ahead hasn't claimed their turn
	                if (data.waitingForUserToClaim) {
	                    this.handleWaitingForClaim(data);
	                }
	            }
	        } catch (error) {
	            console.error('Error checking queue status:', error);
	        }
	    },

    updateQueueDisplay(queueData) {
        // Update queue length
        const queueLength = document.getElementById('queueLength');
        if (queueLength) {
            queueLength.textContent = queueData.queueLength || 0;
        }
        
        // Update estimated wait time
        const estimatedWait = document.getElementById('estimatedWait');
        if (estimatedWait) {
            const minutes = queueData.estimatedWaitTime || 0;
            if (minutes > 0) {
                estimatedWait.textContent = `${minutes} minutes`;
            } else {
                estimatedWait.textContent = 'Ready soon';
            }
        }
        
        // Update waiting list
        const waitingList = document.getElementById('waitingList');
        if (waitingList && queueData.waitingUsers) {
            waitingList.innerHTML = '';
            
            if (queueData.waitingUsers.length > 0) {
                waitingList.innerHTML = '<div class="text-sm text-gray-600 mb-2">Users in queue:</div>';
                
                queueData.waitingUsers.forEach(user => {
                    const userDiv = document.createElement('div');
                    userDiv.className = 'flex items-center justify-between bg-white p-2 rounded border border-orange-200';
                    userDiv.innerHTML = `
                        <div class="flex items-center">
                            <span class="w-6 h-6 bg-orange-500 text-white rounded-full flex items-center justify-center text-xs font-bold mr-2">
                                ${user.position}
                            </span>
                            <span class="text-sm font-medium">${user.email}</span>
                        </div>
                        <span class="text-xs text-gray-500">Waiting ${user.waitTime}</span>
                    `;
                    waitingList.appendChild(userDiv);
                });
                
                if (queueData.queueLength > 3) {
                    const moreDiv = document.createElement('div');
                    moreDiv.className = 'text-center text-sm text-gray-500 mt-2';
                    moreDiv.textContent = `...and ${queueData.queueLength - 3} more users`;
                    waitingList.appendChild(moreDiv);
                }
            } else {
                waitingList.innerHTML = '<div class="text-center text-sm text-gray-500">No users in queue</div>';
            }
        }
    },
	handleYourTurn() {
	        // Show notification that it's their turn
	        this.showTurnNotification();
	        
	        // Start timeout for this user
	        if (!this.queueTimeouts.userTurnTimeout) {
	            this.queueTimeouts.userTurnStartTime = Date.now();
	            this.queueTimeouts.userTurnTimeout = setTimeout(() => {
	                this.forfeitTurn();
	            }, this.queueTimeouts.maxWaitTime);
	        }
	    },
	    
	    // Show notification when it's user's turn
	    showTurnNotification() {
	        // Update modal to show it's their turn
	        const modal = document.getElementById('sessionModal');
	        if (modal.classList.contains('hidden')) {
	            this.showModal();
	        }
	        
	        // Show special "Your Turn!" screen
	        this.showStep('yourTurn');
	        
	        // Play sound notification if possible
	        this.playNotificationSound();
	        
	        // Show browser notification if permitted
	        if (Notification.permission === 'granted') {
	            new Notification('Your turn for HR Demo!', {
	                body: 'You have 2 minutes to start your session.',
	                icon: '/favicon.ico'
	            });
	        }
	    },
		
		handleWaitingForClaim(data) {
		        // Update display to show someone hasn't claimed
		        const waitingNotice = document.getElementById('waitingForClaimNotice');
		        if (waitingNotice) {
		            waitingNotice.classList.remove('hidden');
		            waitingNotice.innerHTML = `
		                <div class="bg-yellow-50 border border-yellow-200 rounded-lg p-3 mb-4">
		                    <p class="text-sm text-yellow-800">
		                        <i class="fas fa-clock mr-2"></i>
		                        Waiting for ${data.waitingForEmail || 'user'} to claim their turn...
		                        <br>
		                        <span class="text-xs">Auto-skip in ${data.remainingClaimTime || '2:00'}</span>
		                    </p>
		                </div>
		            `;
		        }
		    },
		    
		    // Forfeit turn if user doesn't claim
		    async forfeitTurn() {
		        console.log('User forfeited their turn by timeout');
		        
		        try {
		            await fetch('/api/session/forfeit-turn', {
		                method: 'POST',
		                headers: { 'Content-Type': 'application/x-www-form-urlencoded' },
		                body: `email=${encodeURIComponent(userEmail)}`
		            });
		            
		            // Clear timeout
		            if (this.queueTimeouts.userTurnTimeout) {
		                clearTimeout(this.queueTimeouts.userTurnTimeout);
		                this.queueTimeouts.userTurnTimeout = null;
		            }
		            
		            // Show message
		            this.showError('Your turn was skipped due to timeout. Please rejoin the queue.');
		            
		            // Reset to initial state
		            setTimeout(() => {
		                this.checkSessionStatus();
		            }, 3000);
		            
		        } catch (error) {
		            console.error('Error forfeiting turn:', error);
		        }
		    },
		    
		    // Play notification sound
		    playNotificationSound() {
		        try {
		            const audio = new Audio('data:audio/wav;base64,UklGRnoGAABXQVZFZm10IBAAAAABAAEAQB8AAEAfAAABAAgAZGF0YQoGAACBhYqFbF1fdJivrJBhNjVgodDbq2EcBj+a2/LDciUFLIHO8tiJNwgZaLvt559NEAxQp+PwtmMcBjiR1/LMeSwFJHfH8N2QQAoUXrTp66hVFApGn+DyvmwhBSl+zPLZiEAJFGS56+2hVBELTaPn7bllHgg2k9n1w3UnBi56yuzLjjwKF2S56+2hVBELTaPn7bllHgg2k9n1w3UnBi56yuzLjjwKF2S56+2hVBELTaPn7bllHgg2k9n1w3UnBi56yuzLjjwKF2S56+2hVBELTaPn7bllHQctitXyvnErBCh+zPLZiEAJFGS56+2hVBELTaPn7bllHgg2k9n1w3UnBi56yuzLjjwKF2S56+2hVBELTaPn7bllHgg2k9n1w3UnBi56yuzLjjwKF2S56+2hVBELTaPn7bllHgg2k9n1w3UnBi56yuzLjjwKFGO56+yfVBELTaPn7bllHgg2k9n1w3UnBi56yuzLjjwKF2S56+2hVBELTaPn7bllHgg2k9n1w3UnBi56yuzLjjwKF2S56+2hVBELTaPn7bllHgg2k9n1w3UnBi56yuzLjjwKF2S56+2hVBELTaPn7bllHgg2k9n1w3UnBi56yuzLjjwKF2S56+2hVBELTaPn7bllHgg2k9n1w3UnBi56yuzLjjwKF2S56+2hVBELTaPn7bllHgg2k9n1w3UnBi56yuzLjjwKF2S56+2hVBELTaPn7bllHgg2k9n1w3UnBi56yuzLjjwKF2S56+2hVBELTaPn7bllHgg2k9n1w3UnBi56yuzLjjwKF2S56+2hVBELTaPn7bllHgg2k9n1w3UnBi56yuzLjjwKF2S56+2hVREKR6Hn7bllHgg2k9n1w3UnBi56yuzLjjwKF2S56+2hVBELTaPn7bllHgg2k9n1w3UnBi56yuzLjjwKF2S56+2hVBELTaPn7bllHgg2k9n1w3UnBi56yuzLjjwKF2S56+2hVBELTaPn7bllHgg2k9n1w3UnBi56yuzLjjwKF2S56+2hVBELTaPn7bllHgg2k9n1w3UnBi56yuzLjjwKF2S56+2hVBELTaPn7bllHgg2k9n1w3UnBi56yuzLjjwKF2S56+2hVBELTaPn7bllHgg2k9n1w3UnBi56yuzLjjwKF2S56+2hVBELTaPn7bllHgg2k9n1w3UnBi56yuzLjjwKF2S56+2hVBELTaPn7bllHgg2k9n1w3UnBi56yuzLjjwKF2S56+2hVBELTaPn7bllHgg2k9n1w3UnBi56yuzLjjwKF2S56+2hVBELTaPn7bllHgg2k9n1w3UnBi56yuzLjjwKF2S56+2hVBELTaPn7bllHgg2k9n1w3UnBi56yuzLjjwKF2S56+2hVBELTaPn7bllHgg2k9n1w3UnBi56yuzLjjwKF2S56+2hVBELTaPn7bllHgg2k9n1w3UnBi56yuzLjjwKF2S56+2hVBELTaPn7bllHgg2k9n1w3UnBi56yuzLjjwKF2S56+2hVBELTaPn7bllHgg2k9n1w3UnBi56yuzLjjwKF2S56+2hVBELTaPn7bllHgg2k9n1w3UnBi56yuzLjjwKF2S56+2hVA==');
		            audio.play();
		        } catch (e) {
		            console.log('Could not play notification sound');
		        }
		    },
		    
		    // Request notification permission on init
		    requestNotificationPermission() {
		        if ('Notification' in window && Notification.permission === 'default') {
		            Notification.requestPermission();
		        }
		    },
			// User claims their turn
			async claimTurn() {
			    console.log('User claiming their turn');
			    
			    // Clear any timeout
			    if (this.queueTimeouts.userTurnTimeout) {
			        clearTimeout(this.queueTimeouts.userTurnTimeout);
			        this.queueTimeouts.userTurnTimeout = null;
			    }
			    
			    try {
			        const response = await fetch('/api/session/claim-turn', {
			            method: 'POST',
			            headers: { 'Content-Type': 'application/x-www-form-urlencoded' },
			            body: `email=${encodeURIComponent(userEmail)}`
			        });
			        
			        const data = await response.json();
			        
			        if (data.success && data.sessionId) {
			            // Successfully claimed - start session
			            currentSessionId = data.sessionId;
			            document.getElementById('sessionIdDisplay').textContent = data.sessionId.substring(0, 8) + '...';
			            this.showStep('success');
			            this.startSessionTimer(data.sessionDuration * 60);
			            this.startCleanupMonitoring();
			        } else {
			            this.showError(data.message || 'Failed to claim turn');
			        }
			    } catch (error) {
			        console.error('Error claiming turn:', error);
			        this.showError('Failed to start session');
			    }
			},

			// User voluntarily skips their turn
			async skipMyTurn() {
			    if (confirm('Are you sure you want to skip your turn? You\'ll need to rejoin the queue.')) {
			        console.log('User voluntarily skipping turn');
			        
			        // Clear timeout
			        if (this.queueTimeouts.userTurnTimeout) {
			            clearTimeout(this.queueTimeouts.userTurnTimeout);
			            this.queueTimeouts.userTurnTimeout = null;
			        }
			        
			        try {
			            await fetch('/api/session/skip-turn', {
			                method: 'POST',
			                headers: { 'Content-Type': 'application/x-www-form-urlencoded' },
			                body: `email=${encodeURIComponent(userEmail)}`
			            });
			            
			            // Show session active screen again
			            this.checkSessionStatus();
			            
			        } catch (error) {
			            console.error('Error skipping turn:', error);
			        }
			    }
			},

			// Update the timer display for turn countdown
			startTurnCountdown() {
			    let remainingSeconds = 120; // 2 minutes
			    
			    const updateCountdown = () => {
			        const minutes = Math.floor(remainingSeconds / 60);
			        const seconds = remainingSeconds % 60;
			        const display = `${minutes}:${seconds.toString().padStart(2, '0')}`;
			        
			        const countdownEl = document.getElementById('turnCountdown');
			        if (countdownEl) {
			            countdownEl.textContent = display;
			            
			            // Change color as time runs out
			            if (remainingSeconds <= 30) {
			                countdownEl.classList.remove('text-green-600');
			                countdownEl.classList.add('text-red-600');
			            } else if (remainingSeconds <= 60) {
			                countdownEl.classList.remove('text-green-600');
			                countdownEl.classList.add('text-yellow-600');
			            }
			        }
			        
			        if (remainingSeconds <= 0) {
			            clearInterval(this.turnCountdownInterval);
			            return;
			        }
			        
			        remainingSeconds--;
			    };
			    
			    // Clear any existing interval
			    if (this.turnCountdownInterval) {
			        clearInterval(this.turnCountdownInterval);
			    }
			    
			    updateCountdown(); // Initial call
			    this.turnCountdownInterval = setInterval(updateCountdown, 1000);
			},
		
    showSessionActiveStep(data) {
        this.modalState.currentStep = 'sessionActive';
        document.getElementById('currentUserEmail').textContent = data.userEmail || 'Anonymous User';
        
        // Start countdown timer for waiting
        this.startWaitingTimer(data.remainingSeconds || data.remainingMinutes * 60);
        
        // Update queue information
        this.updateQueueDisplay(data);
        
        // Start monitoring queue
        this.startQueueMonitoring();
        
        this.showStep('sessionActive');
        this.showModal();
    },

    handleDataCleanup(cleanupData) {
        console.log('Data cleanup triggered:', cleanupData);
        
        // Don't cleanup if user is actively using the system
        if (this.modalState.isUserInteracting && currentSessionId) {
            console.log('User is active, postponing cleanup');
            return;
        }
        
        // Reset current session
        this.cleanup();
        
        // Show cleanup notice
        this.showCleanupNotice(cleanupData);
        
        // Force reload candidates and UI state
        setTimeout(() => {
            this.resetDemoState();
        }, 2000);
    },

    async checkInitialSessionStatus() {
        try {
            const response = await fetch('/api/session/status');
            const data = await response.json();
            
            console.log('Initial session status:', data);
            
            if (data.sessionExpired && data.dataCleanupRequired) {
                this.handleDataCleanup(data);
            }
            
            if (data.hasActiveSession && !data.available) {
                // Another user has active session
                this.showSessionActiveStep(data);
            } else {
                // No active session, show email entry
                this.enableReadingSections();
                this.showModal();
            }
        } catch (error) {
            console.error('Error checking initial session status:', error);
            this.enableReadingSections();
            this.showModal();
        }
    },
    
    async checkSessionStatus() {
        // Don't check if user is actively using the modal
        if (this.modalState.isUserInteracting) {
            console.log('Skipping session check - user is interacting');
            return;
        }
        
        try {
            const response = await fetch('/api/session/status');
            const data = await response.json();
            
            console.log('Session status check:', data);
            
            if (data.sessionExpired && data.dataCleanupRequired) {
                this.handleDataCleanup(data);
                return;
            }
            
            // Only update modal if we're in the waiting state
            if (this.modalState.currentStep === 'sessionActive') {
                if (data.hasActiveSession && !data.available) {
                    this.updateSessionActiveDisplay(data);
                } else {
                    // Session is now available
                    this.showStep('email');
                }
            }
        } catch (error) {
            console.error('Error checking session status:', error);
            // Don't show error if user is interacting
            if (!this.modalState.isUserInteracting) {
                this.showError('Failed to check session status');
            }
        }
    },
    
    updateSessionActiveDisplay(data) {
        // Update the display without changing steps
        if (document.getElementById('currentUserEmail')) {
            document.getElementById('currentUserEmail').textContent = data.userEmail || 'Anonymous User';
        }
        
        // Update timer if needed
        if (data.remainingSeconds) {
            this.updateWaitingTimer(data.remainingSeconds);
        }
        
        // Update queue information
        this.updateQueueDisplay(data);
    },
    
    updateWaitingTimer(seconds) {
        // Just update the display, don't restart the whole timer
        const minutes = Math.floor(seconds / 60);
        const secs = seconds % 60;
        const timeString = `${minutes}:${secs.toString().padStart(2, '0')}`;
        
        const remainingTimeEl = document.getElementById('remainingTime');
        if (remainingTimeEl) {
            remainingTimeEl.textContent = timeString;
        }
    },

    showModal() {
        console.log('Showing modal');
        this.modalState.isVisible = true;
        document.getElementById('sessionModal').classList.remove('hidden');
        
        // Default to email step if not specified
        if (!this.modalState.currentStep) {
            this.showStep('email');
        }
    },
    
    hideModal() {
        console.log('hideModal called - forcing hide');
        this.modalState.isVisible = false;
        this.modalState.currentStep = null;
        this.modalState.isUserInteracting = false;
        document.getElementById('sessionModal').classList.add('hidden');
    },
    
    showStep(step) {
        console.log('Showing step:', step);
        this.modalState.currentStep = step;
        
        // Hide all steps INCLUDING architectureInfoStep
        ['emailStep', 'verificationStep', 'sessionActiveStep', 'successStep', 'errorStep', 'architectureInfoStep','yourTurnStep'].forEach(id => {
            const element = document.getElementById(id);
            if (element) {
                element.classList.add('hidden');
            }
        });
        
        // Show requested step
        const stepMap = {
            'email': 'emailStep',
            'verification': 'verificationStep', 
            'sessionActive': 'sessionActiveStep',
            'success': 'successStep',
            'error': 'errorStep',
            'architecture': 'architectureInfoStep',
			'yourTurn': 'yourTurnStep'
        };
        
        const stepElement = document.getElementById(stepMap[step]);
        if (stepElement) {
            stepElement.classList.remove('hidden');
        }
        
        // Clear inputs when going back to email
        if (step === 'email') {
            const emailInput = document.getElementById('emailInput');
            const codeInput = document.getElementById('codeInput');
            if (emailInput) emailInput.value = '';
            if (codeInput) codeInput.value = '';
        }
    },
    
    showError(message) {
        console.log('Showing error:', message);
        document.getElementById('errorMessage').textContent = message;
        this.showStep('error');
    },
    
	async requestVerificationCode() {
	    this.trackInteraction();
	    const emailInput = document.getElementById('emailInput');
	    const email = emailInput.value.trim();
	    
	    if (!email) {
	        alert('Please enter your email address');
	        return;
	    }
	    
	    // Email validation
	    const emailRegex = /^[^\s@]+@[^\s@]+\.[^\s@]+$/;
	    if (!emailRegex.test(email)) {
	        alert('Please enter a valid email address');
	        return;
	    }
	    
	    // Normalize email to lowercase for consistency
	    const normalizedEmail = email.toLowerCase().trim();
	    userEmail = normalizedEmail;
	    
	    console.log('Requesting verification for email:', normalizedEmail);
	    console.log('Original email input:', email);
	    
	    const btn = document.getElementById('requestCodeBtn');
	    const originalText = btn.innerHTML;
	    
	    try {
	        btn.innerHTML = '<i class="fas fa-spinner fa-spin mr-2"></i>Sending...';
	        btn.disabled = true;
	        
	        const response = await fetch('/api/session/request-verification', {
	            method: 'POST',
	            headers: { 
	                'Content-Type': 'application/x-www-form-urlencoded',
	                'Accept': 'application/json'
	            },
	            body: `email=${encodeURIComponent(normalizedEmail)}`
	        });
	        
	        const data = await response.json();
	        console.log('Request verification response:', data);
	        
	        if (data.success) {
	            // Display the normalized email
	            document.getElementById('emailDisplay').textContent = normalizedEmail;
	            this.showStep('verification');
	            
	            // Show expiration warning
	            this.showCodeExpirationWarning();
	            
	            // Log success
	            const timestamp = new Date().toLocaleTimeString();
	            console.log(`Verification code requested successfully at ${timestamp} for ${normalizedEmail}`);
	            
	        } else {
	            this.showError(data.message || 'Failed to send verification code');
	        }
	    } catch (error) {
	        console.error('Error requesting verification:', error);
	        this.showError('Failed to send verification code. Please check your connection and try again.');
	    } finally {
	        btn.innerHTML = originalText;
	        btn.disabled = false;
	    }
	},

	// Helper method to show expiration warning
	showCodeExpirationWarning() {
	    const verificationStep = document.getElementById('verificationStep');
	    if (verificationStep) {
	        const existingWarning = verificationStep.querySelector('.expiration-warning');
	        if (!existingWarning) {
	            const formContainer = verificationStep.querySelector('.p-8');
	            if (formContainer) {
	                const warningDiv = document.createElement('div');
	                warningDiv.className = 'expiration-warning bg-yellow-50 border border-yellow-200 rounded-lg p-3 mt-4 fade-in';
	                warningDiv.innerHTML = `
	                    <p class="text-sm text-yellow-800">
	                        <i class="fas fa-clock mr-1"></i>
	                        This code will expire in 10 minutes. Please enter it promptly.
	                    </p>
	                `;
	                formContainer.appendChild(warningDiv);
	            }
	        }
	    }
	},
    
	async verifyCode() {
	    this.trackInteraction();
	    const codeInput = document.getElementById('codeInput');
	    const code = codeInput.value.trim();
	    
	    if (!code) {
	        alert('Please enter the verification code');
	        codeInput.focus();
	        return;
	    }
	    
	    if (!userEmail) {
	        alert('Email not found. Please go back and enter your email again.');
	        this.showStep('email');
	        return;
	    }
	    
	    // Validate code format (6 digits)
	    if (!/^\d{6}$/.test(code)) {
	        alert('Please enter a valid 6-digit code');
	        return;
	    }
	    
	    const btn = document.getElementById('verifyCodeBtn');
	    const originalText = btn.innerHTML;
	    
	    try {
	        btn.innerHTML = '<i class="fas fa-spinner fa-spin mr-2"></i>Verifying...';
	        btn.disabled = true;
	        
	        // Ensure email is normalized (it should already be from requestVerificationCode)
	        const normalizedEmail = userEmail.toLowerCase().trim();
	        
	        console.log('=== VERIFICATION ATTEMPT ===');
	        console.log('Email:', normalizedEmail);
	        console.log('Code:', code);
	        console.log('Timestamp:', new Date().toISOString());
	        console.log('========================');
	        
	        // Create form data
	        const formData = new URLSearchParams();
	        formData.append('email', normalizedEmail);
	        formData.append('code', code);
	        
	        console.log('Sending verification request with:', formData.toString());
	        
	        const response = await fetch('/api/session/verify-email', {
	            method: 'POST',
	            headers: { 
	                'Content-Type': 'application/x-www-form-urlencoded',
	                'Accept': 'application/json'
	            },
	            body: formData.toString()
	        });
	        
	        const data = await response.json();
	        console.log('Verify response:', data, 'Status:', response.status);
	        
	        // Handle successful session start
	        if (response.ok && data.success && data.sessionId) {
	            currentSessionId = data.sessionId;
	            console.log('Session started successfully with ID:', currentSessionId);
	            
	            // Update UI
	            const sessionIdDisplay = document.getElementById('sessionIdDisplay');
	            if (sessionIdDisplay) {
	                sessionIdDisplay.textContent = data.sessionId.substring(0, 8) + '...';
	            }
	            
	            this.showStep('success');
	            this.startSessionTimer((data.sessionDuration || 7) * 60);
	            this.startCleanupMonitoring();
	            
	            // Clear the code input for security
	            codeInput.value = '';
	        } 
	        // Handle 400 error - check specific error messages
	        else if (response.status === 400 && data.message) {
	            if (data.message.includes('No verification code found')) {
	                this.showError('Verification code not found. It may have expired. Please request a new code.');
	                // Optionally go back to email step after delay
	                setTimeout(() => {
	                    this.showStep('email');
	                }, 3000);
	            } else if (data.message.includes('Invalid verification code')) {
	                this.showError('Invalid code. Please check and try again.');
	                // Clear the code input and focus
	                codeInput.value = '';
	                codeInput.focus();
	            } else if (data.message.includes('expired')) {
	                this.showError('Verification code has expired. Please request a new code.');
	                setTimeout(() => {
	                    this.showStep('email');
	                }, 3000);
	            } else if (data.message.includes('queue') || 
	                      data.message.includes('position') || 
	                      data.message.includes('Session unavailable')) {
	                // User added to queue
	                console.log('User added to queue');
	                this.showQueueAddedSuccess();
	            } else {
	                // Other 400 errors
	                this.showError(data.message);
	            }
	        }
	        // Handle other errors
	        else {
	            this.showError(data.message || 'Verification failed. Please try again.');
	        }
	    } catch (error) {
	        console.error('Error verifying code:', error);
	        this.showError('Failed to verify code. Please check your connection and try again.');
	    } finally {
	        // Restore button state after a short delay
	        setTimeout(() => {
	            if (btn) {
	                btn.innerHTML = originalText;
	                btn.disabled = false;
	            }
	        }, 500);
	    }
	},

	// Helper method to show queue success
	showQueueAddedSuccess() {
	    const verificationStep = document.getElementById('verificationStep');
	    if (verificationStep) {
	        verificationStep.innerHTML = `
	            <div class="p-8">
	                <div class="text-center mb-6">
	                    <div class="w-16 h-16 bg-orange-100 rounded-full flex items-center justify-center mx-auto mb-4">
	                        <i class="fas fa-check-circle text-orange-600 text-2xl"></i>
	                    </div>
	                    <h2 class="text-2xl font-bold text-gray-800 mb-2">Successfully Added to Queue!</h2>
	                    <p class="text-gray-600 mb-4">You've been added to the waiting queue.</p>
	                    <div class="bg-orange-50 rounded-lg p-4 mb-6 border border-orange-200">
	                        <p class="text-orange-800 font-semibold mb-2">
	                            <i class="fas fa-sync-alt mr-2"></i>
	                            Please refresh the page to see your queue position
	                        </p>
	                        <p class="text-sm text-orange-700">
	                            Click the button below or press F5 to refresh
	                        </p>
	                    </div>
	                </div>
	                <button onclick="window.location.reload()" 
	                        class="w-full bg-orange-600 hover:bg-orange-700 text-white font-bold py-3 px-6 rounded-lg transition duration-300 flex items-center justify-center">
	                    <i class="fas fa-sync-alt mr-2"></i>
	                    Refresh Page
	                </button>
	            </div>
	        `;
	    }
	},
    // FIX 1: Force modal to close when Start Demo is clicked
    startDemo() {
        console.log('Starting demo - forcing modal close');
        this.trackInteraction();
        
        // Force hide the modal by directly manipulating DOM
        const modal = document.getElementById('sessionModal');
        if (modal) {
            modal.classList.add('hidden');
            modal.style.display = 'none'; // Extra insurance
        }
        
        // Reset modal state completely
        this.modalState.isVisible = false;
        this.modalState.currentStep = null;
        this.modalState.isUserInteracting = false;
        
        // Show session timer
        this.showSessionTimer();
        
        // Enable upload controls
        this.enableUploadControls();
        
        // Enable reading sections
        this.enableReadingSections();
        
        // Small delay before scrolling to ensure modal is hidden
        setTimeout(() => {
            // Remove any lingering modal styles
            const modal = document.getElementById('sessionModal');
            if (modal && !modal.classList.contains('hidden')) {
                modal.classList.add('hidden');
            }
            
            const howItWorksSection = document.querySelector('.bg-blue-50.rounded-xl.p-6.mb-8');
            if (howItWorksSection) {
                howItWorksSection.scrollIntoView({ behavior: 'smooth', block: 'start' });
            }
        }, 300);
    },
    
    async endSession() {
        this.trackInteraction();
        if (!currentSessionId) return;
        
        try {
            await fetch('/api/session/end', {
                method: 'POST',
                headers: { 'Content-Type': 'application/x-www-form-urlencoded' },
                body: `sessionId=${encodeURIComponent(currentSessionId)}`
            });
        } catch (error) {
            console.error('Error ending session:', error);
        }
        
        this.cleanup();
        this.refreshPage();
    },
    
    async validateCurrentSession() {
        if (!currentSessionId) return;
        
        try {
            const response = await fetch('/api/session/status');
            const data = await response.json();
            
            if (data.sessionExpired && data.dataCleanupRequired) {
                this.handleDataCleanup(data);
            } else if (!data.hasActiveSession || data.available) {
                // Session has expired
                this.sessionExpired();
            }
        } catch (error) {
            console.error('Error validating session:', error);
        }
    },
    
    sessionExpired() {
        this.cleanup();
        this.showSessionExpiredNotice();
        
        // Keep reading sections enabled
        this.enableReadingSections();
        
        // Refresh page after 5 seconds
        setTimeout(() => {
            this.refreshPage();
        }, 5000);
    },
    
    startSessionTimer(seconds) {
        if (sessionTimerInterval) {
            clearInterval(sessionTimerInterval);
        }
        
        let remainingSeconds = seconds;
        
        const updateDisplay = () => {
            const minutes = Math.floor(remainingSeconds / 60);
            const secs = remainingSeconds % 60;
            const timeString = `${minutes}:${secs.toString().padStart(2, '0')}`;
            
            // Update both timer displays
            const sessionTimer = document.getElementById('sessionTimer');
            const topTimer = document.getElementById('topTimerDisplay');
            
            if (sessionTimer) sessionTimer.textContent = timeString;
            if (topTimer) topTimer.textContent = timeString;
            
            if (remainingSeconds <= 0) {
                this.sessionExpired();
                return;
            }
            
            remainingSeconds--;
        };
        
        updateDisplay(); // Initial update
        sessionTimerInterval = setInterval(updateDisplay, 1000);
    },
    
    startWaitingTimer(seconds) {
        if (sessionTimerInterval) {
            clearInterval(sessionTimerInterval);
        }
        
        let remainingSeconds = seconds;
        
        const updateDisplay = () => {
            const minutes = Math.floor(remainingSeconds / 60);
            const secs = remainingSeconds % 60;
            const timeString = `${minutes}:${secs.toString().padStart(2, '0')}`;
            
            const remainingTimeEl = document.getElementById('remainingTime');
            if (remainingTimeEl) {
                remainingTimeEl.textContent = timeString;
            }
            
            if (remainingSeconds <= 0) {
                this.checkSessionStatus(); // Check if now available
                return;
            }
            
            remainingSeconds--;
        };
        
        updateDisplay(); // Initial update
        sessionTimerInterval = setInterval(updateDisplay, 1000);
    },
    
    showSessionTimer() {
        document.getElementById('sessionTimerDisplay').classList.remove('hidden');
    },
    
    hideSessionTimer() {
        document.getElementById('sessionTimerDisplay').classList.add('hidden');
    },
    
    showSessionExpiredNotice() {
        document.getElementById('sessionExpiredNotice').classList.remove('hidden');
        this.hideSessionTimer();
        this.disableUploadControls();
        // Keep reading sections enabled
        this.enableReadingSections();
    },
    
    enableReadingSections() {
        // Enable all informational/reading sections
        const readingSections = [
            'agentic-ai-architecture', 
            'how-it-works',
            'tech-stack-info',
            'features-section',
            'footer'
        ];
        
        readingSections.forEach(sectionId => {
            const section = document.getElementById(sectionId);
            if (section) {
                section.style.opacity = '1';
                section.style.pointerEvents = 'auto';
            }
        });
        
        // Enable navigation links
        document.querySelectorAll('a[href*="#"], .nav-back-btn').forEach(link => {
            link.style.pointerEvents = 'auto';
            link.style.opacity = '1';
        });
        
        // Enable viewing processed candidates (read-only)
        const candidatesSection = document.getElementById('candidatesList');
        if (candidatesSection) {
            candidatesSection.style.opacity = '1';
            candidatesSection.style.pointerEvents = 'auto';
        }
        
        // Enable job listings (read-only)
        const jobsSection = document.querySelector('.bg-gray-50'); // Jobs display section
        if (jobsSection) {
            jobsSection.style.opacity = '1';
            jobsSection.style.pointerEvents = 'auto';
        }
    },
    
    enableUploadControls() {
        // Enable ONLY upload functionality (demo section)
        const uploadSection = document.getElementById('uploadProgressSection')?.closest('.bg-white');
        if (uploadSection) {
            uploadSection.style.opacity = '1';
            uploadSection.style.pointerEvents = 'auto';
        }
        
        // Enable file input
        const fileInput = document.getElementById('resumeFiles');
        if (fileInput) {
            fileInput.disabled = false;
        }
        
        // Enable upload buttons
        document.querySelectorAll('button[onclick*="uploadFiles"], button[onclick*="handleFileSelect"]').forEach(btn => {
            btn.disabled = false;
        });
        
        // Enable find matches button
        const findMatchesBtn = document.getElementById('findMatchesBtn');
        if (findMatchesBtn) {
            updateButtonStates(); // Use existing function
        }
    },
    
    disableUploadControls() {
        // Disable ONLY upload functionality (demo section)
        const uploadSection = document.getElementById('uploadProgressSection')?.closest('.bg-white');
        if (uploadSection) {
            uploadSection.style.opacity = '0.5';
            uploadSection.style.pointerEvents = 'none';
        }
        
        // Disable file input
        const fileInput = document.getElementById('resumeFiles');
        if (fileInput) {
            fileInput.disabled = true;
        }
        
        // Disable upload buttons
        document.querySelectorAll('button[onclick*="uploadFiles"], button[onclick*="handleFileSelect"]').forEach(btn => {
            btn.disabled = true;
        });
        
        // Disable find matches button
        const findMatchesBtn = document.getElementById('findMatchesBtn');
        if (findMatchesBtn) {
            findMatchesBtn.disabled = true;
        }
    },
    
    cleanup() {
        console.log('Cleanup called');
		// Reset upload state
		        resetUploadState();
        currentSessionId = null;
        userEmail = null;
        userQueueId = null;
        
        // Clear timers
        if (sessionTimerInterval) {
            clearInterval(sessionTimerInterval);
            sessionTimerInterval = null;
        }
        
        if (cleanupCheckInterval) {
            clearInterval(cleanupCheckInterval);
            cleanupCheckInterval = null;
        }
        
        if (queueCheckInterval) {
            clearInterval(queueCheckInterval);
            queueCheckInterval = null;
        }
        
        // Hide session timer
        this.hideSessionTimer();
        
        // Force hide modal
        this.hideModal();
        
        // Clear queue UI
        const waitingList = document.getElementById('waitingList');
        if (waitingList) waitingList.innerHTML = '';
        
        const queueLength = document.getElementById('queueLength');
        if (queueLength) queueLength.textContent = '0';
        
        const estimatedWait = document.getElementById('estimatedWait');
        if (estimatedWait) estimatedWait.textContent = 'calculating...';
    },
    
    resetDemoState() {
        // Reset all UI elements to initial state
        currentCandidates = [];
        uploadedInSession = false;
        
        // Clear match results
        const matchResults = document.getElementById('matchResults');
        if (matchResults) matchResults.classList.add('hidden');
        
        // Clear upload progress
        const uploadProgressSection = document.getElementById('uploadProgressSection');
        if (uploadProgressSection) uploadProgressSection.classList.add('hidden');
        
        const uploadResults = document.getElementById('uploadResults');
        if (uploadResults) uploadResults.innerHTML = '';
        
        // Reset step indicators
        const step1 = document.getElementById('step1');
        const step2 = document.getElementById('step2');
        if (step1) step1.classList.remove('completed');
        if (step2) step2.classList.remove('completed');
        
        // Reset file input
        const resumeFiles = document.getElementById('resumeFiles');
        if (resumeFiles) resumeFiles.value = '';
        
        // Force reload candidates display
        displayCandidates();
        updateStatistics();
        updateButtonStates();
        
        // Re-enable reading sections
        this.enableReadingSections();
        
        console.log('Demo state reset complete');
    },
    
    showCleanupNotice(cleanupData) {
        const notice = document.createElement('div');
        notice.className = 'fixed top-20 left-1/2 transform -translate-x-1/2 bg-blue-500 text-white rounded-lg shadow-lg p-4 z-50';
        notice.innerHTML = `
            <div class="flex items-center space-x-3">
                <i class="fas fa-sync-alt fa-spin text-2xl"></i>
                <div>
                    <div class="font-bold">Demo Reset Complete</div>
                    <div class="text-sm">Previous session ended. ${cleanupData.candidatesCleared} candidates cleared.</div>
                </div>
            </div>
        `;
        document.body.appendChild(notice);
        
        // Remove notice after 5 seconds
        setTimeout(() => {
            if (notice.parentNode) {
                notice.parentNode.removeChild(notice);
            }
        }, 5000);
    },

    refreshPage() {
        window.location.reload();
    }
};

// ========================================
// TOKEN-BASED ACCESS FUNCTIONS
// ========================================

// Add this function to check for token in URL on page load
function checkForAccessToken() {
    const urlParams = new URLSearchParams(window.location.search);
    const token = urlParams.get('token');
    
    if (token) {
        console.log('Access token found in URL, attempting to start session...');
        startSessionWithToken(token);
    }
}

// Function to start session using token
async function startSessionWithToken(token) {
    try {
        // Show loading state
        showTokenProcessingModal();
        
        const response = await fetch('/api/session/start-with-token', {
            method: 'POST',
            headers: {
                'Content-Type': 'application/x-www-form-urlencoded',
            },
            body: `token=${encodeURIComponent(token)}`
        });
        
        const data = await response.json();
        
        if (response.ok && data.success) {
            // Session started successfully
            currentSessionId = data.sessionId;
            
            // Store the email from the token (if provided)
            if (data.email) {
                userEmail = data.email;
            }
            
            // Hide processing modal
            hideTokenProcessingModal();
            
            // Start the demo immediately
            SessionManager.showStep('success');
            SessionManager.startSessionTimer((data.sessionDuration || 7) * 60);
            SessionManager.startCleanupMonitoring();
            SessionManager.startDemo();
            
            // Remove token from URL
            window.history.replaceState({}, document.title, window.location.pathname);
            
            // Show success notification
            showNotification('Session started successfully! You have 7 minutes to complete the demo.', 'success');
            
        } else {
            // Handle errors
            hideTokenProcessingModal();
            
            if (response.status === 400 && data.message.includes('expired')) {
                showTokenExpiredModal();
            } else if (response.status === 409) {
                showSessionOccupiedModal();
            } else {
                showTokenErrorModal(data.message || 'Failed to start session');
            }
            
            // Remove invalid token from URL
            window.history.replaceState({}, document.title, window.location.pathname);
        }
        
    } catch (error) {
        console.error('Error starting session with token:', error);
        hideTokenProcessingModal();
        showTokenErrorModal('Failed to connect to server. Please try again.');
    }
}

// Modal functions for token processing
function showTokenProcessingModal() {
    const modal = document.createElement('div');
    modal.id = 'tokenProcessingModal';
    modal.className = 'fixed inset-0 bg-gray-600 bg-opacity-50 overflow-y-auto h-full w-full z-50';
    modal.innerHTML = `
        <div class="relative top-20 mx-auto p-5 border w-96 shadow-lg rounded-md bg-white">
            <div class="mt-3 text-center">
                <div class="mx-auto flex items-center justify-center h-12 w-12 rounded-full bg-purple-100 mb-4">
                    <i class="fas fa-spinner fa-spin text-purple-600 text-xl"></i>
                </div>
                <h3 class="text-lg leading-6 font-medium text-gray-900">Processing Your Access</h3>
                <div class="mt-2 px-7 py-3">
                    <p class="text-sm text-gray-500">
                        We're setting up your demo session. This will just take a moment...
                    </p>
                </div>
            </div>
        </div>
    `;
    document.body.appendChild(modal);
}

function hideTokenProcessingModal() {
    const modal = document.getElementById('tokenProcessingModal');
    if (modal) {
        modal.remove();
    }
}

function showTokenExpiredModal() {
    const modal = document.createElement('div');
    modal.className = 'fixed inset-0 bg-gray-600 bg-opacity-50 overflow-y-auto h-full w-full z-50';
    modal.innerHTML = `
        <div class="relative top-20 mx-auto p-5 border w-96 shadow-lg rounded-md bg-white">
            <div class="mt-3 text-center">
                <div class="mx-auto flex items-center justify-center h-12 w-12 rounded-full bg-red-100 mb-4">
                    <i class="fas fa-clock text-red-600 text-xl"></i>
                </div>
                <h3 class="text-lg leading-6 font-medium text-gray-900">Access Link Expired</h3>
                <div class="mt-2 px-7 py-3">
                    <p class="text-sm text-gray-500">
                        Your access link has expired. Access links are only valid for 2 minutes after being sent.
                    </p>
                    <p class="text-sm text-gray-500 mt-2">
                        Please request a new verification code to join the queue again.
                    </p>
                </div>
                <div class="items-center px-4 py-3">
                    <button onclick="this.closest('.fixed').remove(); SessionManager.showModal();"
                            class="px-4 py-2 bg-purple-600 text-white text-base font-medium rounded-md w-full shadow-sm hover:bg-purple-700 focus:outline-none focus:ring-2 focus:ring-purple-500">
                        Request New Access
                    </button>
                </div>
            </div>
        </div>
    `;
    document.body.appendChild(modal);
}

function showSessionOccupiedModal() {
    const modal = document.createElement('div');
    modal.className = 'fixed inset-0 bg-gray-600 bg-opacity-50 overflow-y-auto h-full w-full z-50';
    modal.innerHTML = `
        <div class="relative top-20 mx-auto p-5 border w-96 shadow-lg rounded-md bg-white">
            <div class="mt-3 text-center">
                <div class="mx-auto flex items-center justify-center h-12 w-12 rounded-full bg-orange-100 mb-4">
                    <i class="fas fa-users text-orange-600 text-xl"></i>
                </div>
                <h3 class="text-lg leading-6 font-medium text-gray-900">Session Currently Occupied</h3>
                <div class="mt-2 px-7 py-3">
                    <p class="text-sm text-gray-500">
                        Another user is currently using the demo. This can happen if someone else claimed their turn just before you.
                    </p>
                    <p class="text-sm text-gray-500 mt-2">
                        Please join the queue again to get a new turn.
                    </p>
                </div>
                <div class="items-center px-4 py-3">
                    <button onclick="this.closest('.fixed').remove(); SessionManager.showModal();"
                            class="px-4 py-2 bg-orange-600 text-white text-base font-medium rounded-md w-full shadow-sm hover:bg-orange-700 focus:outline-none focus:ring-2 focus:ring-orange-500">
                        Join Queue
                    </button>
                </div>
            </div>
        </div>
    `;
    document.body.appendChild(modal);
}

function showTokenErrorModal(message) {
    const modal = document.createElement('div');
    modal.className = 'fixed inset-0 bg-gray-600 bg-opacity-50 overflow-y-auto h-full w-full z-50';
    modal.innerHTML = `
        <div class="relative top-20 mx-auto p-5 border w-96 shadow-lg rounded-md bg-white">
            <div class="mt-3 text-center">
                <div class="mx-auto flex items-center justify-center h-12 w-12 rounded-full bg-red-100 mb-4">
                    <i class="fas fa-exclamation-triangle text-red-600 text-xl"></i>
                </div>
                <h3 class="text-lg leading-6 font-medium text-gray-900">Access Error</h3>
                <div class="mt-2 px-7 py-3">
                    <p class="text-sm text-gray-500">${message}</p>
                </div>
                <div class="items-center px-4 py-3">
                    <button onclick="this.closest('.fixed').remove();"
                            class="px-4 py-2 bg-gray-600 text-white text-base font-medium rounded-md w-full shadow-sm hover:bg-gray-700 focus:outline-none focus:ring-2 focus:ring-gray-500">
                        Close
                    </button>
                </div>
            </div>
        </div>
    `;
    document.body.appendChild(modal);
}

// Notification helper
function showNotification(message, type = 'info') {
    const notification = document.createElement('div');
    const bgColor = type === 'success' ? 'bg-green-500' : type === 'error' ? 'bg-red-500' : 'bg-blue-500';
    
    notification.className = `fixed top-4 right-4 ${bgColor} text-white px-6 py-3 rounded-lg shadow-lg z-50 flex items-center space-x-3`;
    notification.innerHTML = `
        <i class="fas ${type === 'success' ? 'fa-check-circle' : type === 'error' ? 'fa-exclamation-circle' : 'fa-info-circle'}"></i>
        <span>${message}</span>
    `;
    
    document.body.appendChild(notification);
    
    // Auto-remove after 5 seconds
    setTimeout(() => {
        notification.style.opacity = '0';
        notification.style.transition = 'opacity 0.5s';
        setTimeout(() => notification.remove(), 500);
    }, 5000);
}

// ========================================
// DEBUG FUNCTIONS
// ========================================
window.debugHR = {
    getCurrentCandidates: () => currentCandidates,
    getCurrentJobs: () => currentJobs,
    reloadCandidates: loadCandidates,
    reloadJobs: loadJobs,
    checkAPI: async () => {
        try {
            const response = await fetch('/api/candidates');
            const data = await response.json();
            console.log('API check result:', data);
            return data;
        } catch (error) {
            console.error('API check failed:', error);
            return error;
        }
    }
};

// New function to freeze upload section
function freezeUploadSection() {
    console.log('Freezing upload section');
    
    // Disable file input
    const fileInput = document.getElementById('resumeFiles');
    if (fileInput) {
        fileInput.disabled = true;
    }
    
    // Update upload zone to show locked state
    const uploadZone = document.querySelector('.bg-red-50.border-2.border-dashed.border-red-300');
    if (uploadZone) {
        uploadZone.classList.remove('hover:bg-red-100', 'transition-colors', 'cursor-pointer');
        uploadZone.classList.add('bg-gray-100', 'border-gray-400', 'cursor-not-allowed');
        uploadZone.style.pointerEvents = 'none';
        
        // Update upload zone content
        uploadZone.innerHTML = `
            <div class="mb-4">
                <i class="fas fa-lock text-6xl text-gray-400"></i>
            </div>
            <h3 class="text-lg font-semibold text-gray-600 mb-2">Upload Section Locked</h3>
            <div class="bg-gray-200 text-gray-700 font-bold py-2 px-6 rounded-lg">
                <i class="fas fa-check-circle mr-2"></i>
                Files Uploaded Successfully
            </div>
            <p class="text-sm text-gray-500 mt-3">
                <i class="fas fa-info-circle mr-1"></i>
                Upload is locked for this session. Proceed to job matching below.
            </p>
        `;
    }
    
    // Disable download sample button
    const downloadBtn = document.querySelector('button[onclick="downloadSampleResumes()"]');
    if (downloadBtn) {
        downloadBtn.disabled = true;
        downloadBtn.classList.add('opacity-50', 'cursor-not-allowed');
        downloadBtn.innerHTML = '<i class="fas fa-lock mr-2"></i>Session Active';
    }
}

// Function to maintain upload freeze state
function maintainUploadFreeze() {
    const uploadSection = document.querySelector('.bg-white.rounded-xl.shadow-lg.p-8.mb-8.card-hover');
    if (uploadSection && uploadSection.querySelector('#step1')) {
        uploadSection.classList.add('border-l-4', 'border-green-500');
        uploadSection.style.background = 'linear-gradient(135deg, #f0fdf4 0%, #ecfdf5 100%)';
        
        // Add completion badge to step 1
        const step1 = document.getElementById('step1');
        if (step1) {
            step1.innerHTML = '<i class="fas fa-check text-white"></i>';
            step1.classList.add('bg-green-500');
            step1.classList.remove('bg-purple-600');
        }
    }
}

// Function to scroll to job selection section
function scrollToJobSelection() {
    const jobSection = document.getElementById('step2');
    if (jobSection) {
        setTimeout(() => {
            jobSection.closest('.bg-white.rounded-xl').scrollIntoView({ 
                behavior: 'smooth', 
                block: 'start' 
            });
        }, 1000);
    }
}

// Function to reset upload state
function resetUploadState() {
    uploadSessionActive = false;
    hasUploadedInCurrentSession = false;
    
    // Reset file input
    const fileInput = document.getElementById('resumeFiles');
    if (fileInput) {
        fileInput.disabled = false;
    }
    
    // Reset step indicators
    const step1 = document.getElementById('step1');
    if (step1) {
        step1.innerHTML = '1';
        step1.classList.remove('completed', 'bg-green-500');
        step1.classList.add('bg-purple-600');
    }
    
    // Reset upload section styling
    const uploadSection = document.querySelector('.bg-white.rounded-xl.shadow-lg.p-8.mb-8');
    if (uploadSection && uploadSection.querySelector('#step1')) {
        uploadSection.classList.remove('border-l-4', 'border-green-500');
        uploadSection.style.background = '';
    }
    
    // Reset upload zone
    const uploadZone = document.querySelector('.bg-gray-100.border-gray-400');
    if (uploadZone) {
        uploadZone.classList.remove('bg-gray-100', 'border-gray-400', 'cursor-not-allowed');
        uploadZone.classList.add('bg-red-50', 'border-red-300', 'hover:bg-red-100', 'transition-colors', 'cursor-pointer');
        uploadZone.style.pointerEvents = 'auto';
        
        // Restore original content
        uploadZone.innerHTML = `
            <div class="mb-4">
                <i class="fas fa-file-pdf text-6xl text-red-400"></i>
            </div>
            <input type="file" id="resumeFiles" multiple accept=".pdf"
                   class="hidden" onchange="handleFileSelect()">
            <h3 class="text-lg font-semibold text-gray-800 mb-2">Click to Upload PDF Resumes</h3>
            <button class="bg-red-600 hover:bg-red-700 text-white font-bold py-2 px-6 rounded-lg transition duration-300">
                <i class="fas fa-file-pdf mr-2"></i>
                Browse PDF Files
            </button>
            <p class="text-sm text-gray-500 mt-3">
                <i class="fas fa-info-circle mr-1"></i>
                PDF files only (Max 10MB each) • Multiple files supported
            </p>
        `;
    }
    
    // Reset download button
    const downloadBtn = document.querySelector('button[onclick="downloadSampleResumes()"]');
    if (downloadBtn) {
        downloadBtn.disabled = false;
        downloadBtn.classList.remove('opacity-50', 'cursor-not-allowed');
        downloadBtn.innerHTML = '<i class="fas fa-download mr-2"></i>Download PDF Test Data';
    }
    
    // Hide progress section
    const progressSection = document.getElementById('uploadProgressSection');
    if (progressSection) progressSection.classList.add('hidden');
    
    // Clear results
    const resultsDiv = document.getElementById('uploadResults');
    if (resultsDiv) resultsDiv.innerHTML = '';
}

console.log('HR Agent JavaScript loaded successfully');
console.log('Debug functions available via window.debugHR');