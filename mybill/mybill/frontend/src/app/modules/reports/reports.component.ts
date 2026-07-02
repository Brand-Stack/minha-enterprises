import { Component, ElementRef, OnInit } from '@angular/core';
import { gsap } from 'gsap';

@Component({
  selector: 'app-reports',
  templateUrl: './reports.component.html',
  styleUrls: ['./reports.component.scss']
})
export class ReportsComponent {
  selectedTabIndex = 0;

  constructor(private host: ElementRef<HTMLElement>) {}

  onTabChange(index: number): void {
    this.selectedTabIndex = index;
    if (window.matchMedia('(prefers-reduced-motion: reduce)').matches) {
      return;
    }
    const card = this.host.nativeElement.querySelector('.reports-card');
    if (card) {
      gsap.fromTo(
        card,
        { boxShadow: '0 18px 50px rgba(15, 23, 42, 0.12)' },
        { boxShadow: '0 18px 50px rgba(15, 23, 42, 0.08)', duration: 0.35, ease: 'power2.out' }
      );
    }
  }
}
