// Theme Management with localStorage persistence
(function() {
    'use strict';
    
    const STORAGE_KEY = 'theme-preference';
    const THEME_LIGHT = 'light';
    const THEME_DARK = 'dark';
    
    // Get theme preference from localStorage or system preference
    function getPreferredTheme() {
        const storedTheme = localStorage.getItem(STORAGE_KEY);
        
        if (storedTheme) {
            return storedTheme;
        }
        
        // Check system preference
        if (window.matchMedia && window.matchMedia('(prefers-color-scheme: dark)').matches) {
            return THEME_DARK;
        }
        
        return THEME_LIGHT;
    }
    
    // Set theme on document
    function setTheme(theme) {
        document.documentElement.setAttribute('data-theme', theme);
        localStorage.setItem(STORAGE_KEY, theme);
        
        // Update meta theme-color for mobile browsers
        const metaThemeColor = document.querySelector('meta[name="theme-color"]');
        if (metaThemeColor) {
            metaThemeColor.setAttribute('content', 
                theme === THEME_DARK ? '#1a1a1a' : '#ffffff'
            );
        } else {
            const meta = document.createElement('meta');
            meta.name = 'theme-color';
            meta.content = theme === THEME_DARK ? '#1a1a1a' : '#ffffff';
            document.head.appendChild(meta);
        }
    }
    
    // Toggle between themes
    function toggleTheme() {
        const currentTheme = document.documentElement.getAttribute('data-theme') || THEME_LIGHT;
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
        
        // Listen for system theme changes
        if (window.matchMedia) {
            window.matchMedia('(prefers-color-scheme: dark)').addEventListener('change', function(e) {
                // Only auto-update if user hasn't manually set a preference
                if (!localStorage.getItem(STORAGE_KEY)) {
                    setTheme(e.matches ? THEME_DARK : THEME_LIGHT);
                }
            });
        }
    });
    
    // Add smooth scrolling for anchor links
    document.addEventListener('DOMContentLoaded', function() {
        document.querySelectorAll('a[href^="#"]').forEach(anchor => {
            anchor.addEventListener('click', function(e) {
                const href = this.getAttribute('href');
                if (href === '#') return;
                
                e.preventDefault();
                const target = document.querySelector(href);
                
                if (target) {
                    const offsetTop = target.offsetTop - 80; // Account for fixed navbar
                    window.scrollTo({
                        top: offsetTop,
                        behavior: 'smooth'
                    });
                    
                    // Update URL without jumping
                    history.pushState(null, null, href);
                }
            });
        });
    });
    
    // Add animation on scroll for cards
    document.addEventListener('DOMContentLoaded', function() {
        const observerOptions = {
            threshold: 0.1,
            rootMargin: '0px 0px -50px 0px'
        };
        
        const observer = new IntersectionObserver(function(entries) {
            entries.forEach(entry => {
                if (entry.isIntersecting) {
                    entry.target.style.animation = 'fadeInUp 0.6s ease forwards';
                    observer.unobserve(entry.target);
                }
            });
        }, observerOptions);
        
        document.querySelectorAll('.card, .feature-list li, .doc-card').forEach(el => {
            el.style.opacity = '0';
            observer.observe(el);
        });
    });
    
})();
