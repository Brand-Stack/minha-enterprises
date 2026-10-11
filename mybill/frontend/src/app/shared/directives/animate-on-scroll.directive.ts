import { Directive, ElementRef, OnInit, OnDestroy } from '@angular/core';
import { gsap } from 'gsap';

@Directive({
  selector: '[appAnimateOnScroll]'
})
export class AnimateOnScrollDirective implements OnInit, OnDestroy {
  private observer?: IntersectionObserver;
  
  constructor(private el: ElementRef) {}

  ngOnInit() {
    if (window.matchMedia('(prefers-reduced-motion: reduce)').matches) {
      return;
    }

    // Use IntersectionObserver for scroll-triggered animations
    this.observer = new IntersectionObserver((entries) => {
      entries.forEach(entry => {
        if (entry.isIntersecting) {
          gsap.fromTo(entry.target,
            { opacity: 0, y: 50 },
            { opacity: 1, y: 0, duration: 0.8, ease: 'power3.out' }
          );
          this.observer?.unobserve(entry.target);
        }
      });
    }, { threshold: 0.1 });

    this.observer.observe(this.el.nativeElement);
  }

  ngOnDestroy() {
    if (this.observer) {
      this.observer.disconnect();
    }
  }
}
