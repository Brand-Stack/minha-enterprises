import { NgModule } from '@angular/core';
import { RouterModule, Routes } from '@angular/router';
import { ZoneConfigurationListComponent } from './zone-configuration-list/zone-configuration-list.component';
import { ZoneConfigurationFormComponent } from './zone-configuration-form/zone-configuration-form.component';
import { LayoutComponent } from '../../dashboard/layout/layout.component';

const routes: Routes = [
    {
        path: '',
        component: LayoutComponent,
        children: [
            { path: '', component: ZoneConfigurationListComponent },
            { path: 'new', component: ZoneConfigurationFormComponent },
            { path: ':id/edit', component: ZoneConfigurationFormComponent }
        ]
    }
];

@NgModule({
    imports: [RouterModule.forChild(routes)],
    exports: [RouterModule]
})
export class ZoneConfigurationRoutingModule { }
