// Theme Management with localStorage persistence
(function() {
    'use strict';
    
    const STORAGE_KEY = 'theme-preference';
    const THEME_LIGHT = 'light';
    const THEME_DARK = 'dark';
    
    // Get theme preference - default to dark
    function getPreferredTheme() {
        const storedTheme = localStorage.getItem(STORAGE_KEY);
        
        if (storedTheme) {
            return storedTheme;
        }
        
        // Default to dark mode
        return THEME_DARK;
    }
    
    // Set theme on document
    function setTheme(theme) {
        document.documentElement.setAttribute('data-theme', theme);
        localStorage.setItem(STORAGE_KEY, theme);
        
        // Update meta theme-color for mobile browsers
        const metaThemeColor = document.querySelector('meta[name="theme-color"]');
        if (metaThemeColor) {
            metaThemeColor.setAttribute('content', 
                theme === THEME_DARK ? '#0d1117' : '#ffffff'
            );
        } else {
            const meta = document.createElement('meta');
            meta.name = 'theme-color';
            meta.content = theme === THEME_DARK ? '#0d1117' : '#ffffff';
            document.head.appendChild(meta);
        }
    }
    
    // Toggle between themes
    function toggleTheme() {
        const currentTheme = document.documentElement.getAttribute('data-theme') || THEME_DARK;
        const newTheme = currentTheme === THEME_LIGHT ? THEME_DARK : THEME_LIGHT;
        
        // Add smooth transition
        document.documentElement.style.transition = 'background-color 0.3s ease, color 0.3s ease';
        
        setTheme(newTheme);
        
        // Remove transition after animation completes
        setTimeout(() => {
            document.documentElement.style.transition = '';
        }, 300);
        
        // Trigger custom event for other scripts
        window.dispatchEvent(new CustomEvent('themechange', { detail: { theme: newTheme } }));
    }
    
    // Initialize theme on page load (before DOM ready to prevent flash)
    setTheme(getPreferredTheme());
    
    // Setup toggle button when DOM is ready
    document.addEventListener('DOMContentLoaded', function() {
        const toggleButton = document.getElementById('theme-toggle');
        
        if (toggleButton) {
            toggleButton.addEventListener('click', toggleTheme);
            
            // Add keyboard support
            toggleButton.addEventListener('keydown', function(e) {
                if (e.key === 'Enter' || e.key === ' ') {
                    e.preventDefault();
                    toggleTheme();
                }
            });
        }
    });
    
})();
