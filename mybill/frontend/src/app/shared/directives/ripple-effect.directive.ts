import { Directive, ElementRef, HostListener, Renderer2 } from '@angular/core';
import { gsap } from 'gsap';

@Directive({
  selector: '[appRipple]'
})
export class RippleEffectDirective {
  
  constructor(
    private el: ElementRef,
    private renderer: Renderer2
  ) {
    this.renderer.setStyle(this.el.nativeElement, 'position', 'relative');
    this.renderer.setStyle(this.el.nativeElement, 'overflow', 'hidden');
  }

  @HostListener('click', ['$event'])
  onClick(event: MouseEvent) {
    if (window.matchMedia('(prefers-reduced-motion: reduce)').matches) return;
    
    const ripple = this.renderer.createElement('span');
    const rect = this.el.nativeElement.getBoundingClientRect();
    const size = Math.max(rect.width, rect.height);
    const x = event.clientX - rect.left - size / 2;
    const y = event.clientY - rect.top - size / 2;
    
    this.renderer.setStyle(ripple, 'position', 'absolute');
    this.renderer.setStyle(ripple, 'border-radius', '50%');
    this.renderer.setStyle(ripple, 'background', 'rgba(255, 255, 255, 0.6)');
    this.renderer.setStyle(ripple, 'width', size + 'px');
    this.renderer.setStyle(ripple, 'height', size + 'px');
    this.renderer.setStyle(ripple, 'left', x + 'px');
    this.renderer.setStyle(ripple, 'top', y + 'px');
    this.renderer.setStyle(ripple, 'pointer-events', 'none');
    this.renderer.setStyle(ripple, 'transform', 'scale(0)');
    
    this.renderer.appendChild(this.el.nativeElement, ripple);
    
    gsap.to(ripple, {
      scale: 4,
      opacity: 0,
      duration: 0.6,
      ease: 'power2.out',
      onComplete: () => this.renderer.removeChild(this.el.nativeElement, ripple)
    });
  }
}

