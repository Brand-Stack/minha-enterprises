import { Component, OnInit, OnDestroy, AfterViewInit, ElementRef, ViewChild } from '@angular/core';
import { gsap } from 'gsap';

@Component({
  selector: 'app-loading-spinner',
  template: `
    <div #spinnerContainer class="fixed inset-0 bg-black bg-opacity-50 flex items-center justify-center z-50">
      <div #spinner class="relative w-16 h-16">
        <div class="absolute inset-0 border-4 border-blue-200 rounded-full"></div>
        <div #spinnerRing class="absolute inset-0 border-4 border-transparent border-t-blue-600 rounded-full"></div>
      </div>
    </div>
  `,
  styles: [`
    :host {
      display: block;
    }
  `]
})
export class LoadingSpinnerComponent implements OnInit, AfterViewInit, OnDestroy {
  @ViewChild('spinnerContainer') spinnerContainer!: ElementRef;
  @ViewChild('spinnerRing') spinnerRing!: ElementRef;
  
  private animation: any;

  ngOnInit() {
    // ViewChild is not available in ngOnInit, moved to ngAfterViewInit
  }

  ngAfterViewInit() {
    if (window.matchMedia('(prefers-reduced-motion: reduce)').matches) return;
    
    if (!this.spinnerRing?.nativeElement || !this.spinnerContainer?.nativeElement) return;
    
    this.animation = gsap.to(this.spinnerRing.nativeElement, {
      rotation: 360,
      duration: 1,
      repeat: -1,
      ease: 'linear'
    });
    
    gsap.fromTo(this.spinnerContainer.nativeElement,
      { opacity: 0 },
      { opacity: 1, duration: 0.3 }
    );
  }

  ngOnDestroy() {
    if (this.animation) {
      this.animation.kill();
    }
  }
}

