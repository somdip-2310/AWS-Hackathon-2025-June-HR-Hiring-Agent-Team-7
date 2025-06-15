// UNIFIED main.js - Complete HR Agent JavaScript
// Replace ALL your separate JS files with this single file

// ========================================
// GLOBAL VARIABLES
// ========================================
let currentJobs = [];
let currentCandidates = [];
let uploadedInSession = false;

// ========================================
// INITIALIZATION
// ========================================
document.addEventListener('DOMContentLoaded', function() {
    console.log('HR Agent initializing...');
    
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
        
        const progressItem = document.getElementById(`progress-${i}`);
        const progressBar = progressItem?.querySelector('.progress-bar');
        const statusText = progressItem?.querySelector('.status-text');
        const statusIcon = progressItem?.querySelector('.status-icon');

        try {
            console.log(`Uploading file ${i + 1}/${files.length}:`, file.name);
            
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

console.log('HR Agent JavaScript loaded successfully');
console.log('Debug functions available via window.debugHR');