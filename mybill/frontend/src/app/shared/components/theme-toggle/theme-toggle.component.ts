import { Component, OnInit, OnDestroy } from '@angular/core';
import { gsap } from 'gsap';

@Component({
  selector: 'app-theme-toggle',
  template: `
    <button #toggleButton
            (click)="toggleTheme()"
            class="relative w-14 h-8 rounded-full bg-gray-300 dark:bg-gray-700 transition-colors duration-300 focus:outline-none focus:ring-2 focus:ring-blue-500">
      <span #toggleCircle
            [class]="'absolute top-1 left-1 w-6 h-6 bg-white rounded-full shadow-md transform transition-transform duration-300 ' + (isDark ? 'translate-x-6' : 'translate-x-0')">
        <mat-icon class="text-xs mt-1.5 ml-1.5">{{ isDark ? 'dark_mode' : 'light_mode' }}</mat-icon>
      </span>
    </button>
  `,
  styles: [`
    :host {
      display: inline-block;
    }
  `]
})
export class ThemeToggleComponent implements OnInit, OnDestroy {
  isDark = false;
  private toggleButton?: HTMLElement;
  private toggleCircle?: HTMLElement;

  ngOnInit() {
    // Check saved theme or system preference
    const savedTheme = localStorage.getItem('theme');
    const prefersDark = window.matchMedia('(prefers-color-scheme: dark)').matches;
    this.isDark = savedTheme === 'dark' || (!savedTheme && prefersDark);
    this.applyTheme();
  }

  ngOnDestroy() {
    // Cleanup if needed
  }

  toggleTheme() {
    this.isDark = !this.isDark;
    this.applyTheme();
    localStorage.setItem('theme', this.isDark ? 'dark' : 'light');
    
    if (!window.matchMedia('(prefers-reduced-motion: reduce)').matches) {
      // Animate theme transition
      gsap.to(document.documentElement, {
        '--theme-transition': '0.3s',
        duration: 0.3,
        ease: 'power2.out'
      });
    }
  }

  private applyTheme() {
    if (this.isDark) {
      document.documentElement.classList.add('dark');
    } else {
      document.documentElement.classList.remove('dark');
    }
  }
}

