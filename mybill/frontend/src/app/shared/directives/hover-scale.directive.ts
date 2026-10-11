import { Directive, ElementRef, HostListener } from '@angular/core';
import { gsap } from 'gsap';

@Directive({
  selector: '[appHoverScale]'
})
export class HoverScaleDirective {
  
  constructor(private el: ElementRef) {}

  @HostListener('mouseenter')
  onMouseEnter() {
    if (window.matchMedia('(prefers-reduced-motion: reduce)').matches) return;
    gsap.to(this.el.nativeElement, { scale: 1.05, duration: 0.2, ease: 'power2.out' });
  }

  @HostListener('mouseleave')
  onMouseLeave() {
    if (window.matchMedia('(prefers-reduced-motion: reduce)').matches) return;
    gsap.to(this.el.nativeElement, { scale: 1, duration: 0.2, ease: 'power2.out' });
  }
}

