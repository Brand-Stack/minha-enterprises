import { Injectable } from '@angular/core';
import { gsap } from 'gsap';

@Injectable({
  providedIn: 'root'
})
export class AnimationService {
  
  private prefersReducedMotion = window.matchMedia('(prefers-reduced-motion: reduce)').matches;

  constructor() {
    // Respect user's motion preferences
    if (this.prefersReducedMotion) {
      gsap.config({ nullTargetWarn: false });
    }
  }

  /**
   * Fade in animation
   */
  fadeIn(element: HTMLElement | null | undefined, duration: number = 0.5, delay: number = 0) {
    if (!element) return;
    if (this.prefersReducedMotion) {
      element.style.opacity = '1';
      return;
    }
    gsap.fromTo(element, 
      { opacity: 0 },
      { opacity: 1, duration, delay, ease: 'power2.out' }
    );
  }

  /**
   * Slide in from direction
   */
  slideIn(element: HTMLElement, direction: 'left' | 'right' | 'up' | 'down' = 'left', duration: number = 0.6) {
    if (this.prefersReducedMotion) {
      element.style.transform = 'translate(0, 0)';
      return;
    }
    
    const from: any = { opacity: 0 };
    const to: any = { opacity: 1, duration, ease: 'power3.out' };
    
    switch (direction) {
      case 'left':
        from.x = -50;
        to.x = 0;
        break;
      case 'right':
        from.x = 50;
        to.x = 0;
        break;
      case 'up':
        from.y = 50;
        to.y = 0;
        break;
      case 'down':
        from.y = -50;
        to.y = 0;
        break;
    }
    
    gsap.fromTo(element, from, to);
  }

  /**
   * Scale animation
   */
  scaleIn(element: HTMLElement | null | undefined, duration: number = 0.4) {
    if (!element) return;
    if (this.prefersReducedMotion) {
      element.style.transform = 'scale(1)';
      return;
    }
    gsap.fromTo(element,
      { scale: 0.8, opacity: 0 },
      { scale: 1, opacity: 1, duration, ease: 'back.out(1.7)' }
    );
  }

  /**
   * Stagger animation for list items
   */
  staggerIn(elements: HTMLElement[], duration: number = 0.3, stagger: number = 0.1) {
    if (this.prefersReducedMotion) {
      elements.forEach(el => el.style.opacity = '1');
      return;
    }
    gsap.fromTo(elements,
      { opacity: 0, y: 20 },
      { opacity: 1, y: 0, duration, stagger, ease: 'power2.out' }
    );
  }

  /**
   * Hover scale effect
   */
  hoverScale(element: HTMLElement, scale: number = 1.05) {
    if (this.prefersReducedMotion) return;
    
    element.addEventListener('mouseenter', () => {
      gsap.to(element, { scale, duration: 0.2, ease: 'power2.out' });
    });
    
    element.addEventListener('mouseleave', () => {
      gsap.to(element, { scale: 1, duration: 0.2, ease: 'power2.out' });
    });
  }

  /**
   * Button ripple effect
   */
  rippleEffect(event: MouseEvent, button: HTMLElement) {
    if (this.prefersReducedMotion) return;
    
    const ripple = document.createElement('span');
    const rect = button.getBoundingClientRect();
    const size = Math.max(rect.width, rect.height);
    const x = event.clientX - rect.left - size / 2;
    const y = event.clientY - rect.top - size / 2;
    
    ripple.style.width = ripple.style.height = size + 'px';
    ripple.style.left = x + 'px';
    ripple.style.top = y + 'px';
    ripple.classList.add('ripple');
    
    button.appendChild(ripple);
    
    gsap.fromTo(ripple,
      { scale: 0, opacity: 0.6 },
      { scale: 4, opacity: 0, duration: 0.6, ease: 'power2.out', onComplete: () => ripple.remove() }
    );
  }

  /**
   * Shake animation for errors
   */
  shake(element: HTMLElement) {
    if (this.prefersReducedMotion) return;
    
    gsap.to(element, {
      x: -10,
      duration: 0.1,
      repeat: 5,
      yoyo: true,
      ease: 'power2.inOut',
      onComplete: () => {
        gsap.set(element, { x: 0 });
      }
    });
  }

  /**
   * Count up animation
   */
  countUp(element: HTMLElement, targetValue: number, duration: number = 2) {
    if (this.prefersReducedMotion) {
      element.textContent = targetValue.toString();
      return;
    }
    
    const obj = { value: 0 };
    gsap.to(obj, {
      value: targetValue,
      duration,
      ease: 'power2.out',
      onUpdate: () => {
        element.textContent = Math.round(obj.value).toString();
      }
    });
  }

  /**
   * Page transition
   */
  pageTransition(outElement: HTMLElement, inElement: HTMLElement, callback?: () => void) {
    if (this.prefersReducedMotion) {
      if (callback) callback();
      return;
    }
    
    const tl = gsap.timeline();
    tl.to(outElement, { opacity: 0, duration: 0.3 })
      .set(inElement, { opacity: 0 })
      .call(() => { if (callback) callback(); })
      .to(inElement, { opacity: 1, duration: 0.3 });
  }
}

