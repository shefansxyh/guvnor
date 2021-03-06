/*
 * Copyright 2012 JBoss Inc
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *       http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package org.kie.guvnor.guided.rule.client.widget;

import com.google.gwt.event.dom.client.ChangeEvent;
import com.google.gwt.event.dom.client.ChangeHandler;
import com.google.gwt.event.dom.client.ClickEvent;
import com.google.gwt.event.dom.client.ClickHandler;
import com.google.gwt.event.shared.EventBus;
import com.google.gwt.user.client.Command;
import com.google.gwt.user.client.Window;
import com.google.gwt.user.client.ui.HorizontalPanel;
import com.google.gwt.user.client.ui.Image;
import com.google.gwt.user.client.ui.ListBox;
import com.google.gwt.user.client.ui.Widget;
import org.drools.guvnor.models.commons.shared.rule.ActionFieldValue;
import org.drools.guvnor.models.commons.shared.rule.ActionInsertFact;
import org.drools.guvnor.models.commons.shared.rule.ActionSetField;
import org.drools.guvnor.models.commons.shared.rule.ActionUpdateField;
import org.kie.guvnor.commons.ui.client.resources.HumanReadable;
import org.kie.guvnor.commons.ui.client.popups.errors.ErrorPopup;
import org.kie.guvnor.datamodel.model.DropDownData;
import org.kie.guvnor.datamodel.model.FieldAccessorsAndMutators;
import org.kie.guvnor.datamodel.oracle.PackageDataModelOracle;
import org.kie.guvnor.guided.rule.client.editor.ActionValueEditor;
import org.kie.guvnor.guided.rule.client.editor.RuleModeller;
import org.kie.guvnor.guided.rule.client.editor.events.TemplateVariablesChangedEvent;
import org.kie.guvnor.guided.rule.client.resources.i18n.Constants;
import org.kie.guvnor.guided.rule.client.resources.images.GuidedRuleEditorImages508;
import org.kie.guvnor.guided.rule.client.util.FieldNatureUtil;
import org.uberfire.client.common.ClickableLabel;
import org.uberfire.client.common.DirtyableFlexTable;
import org.uberfire.client.common.FormStylePopup;
import org.uberfire.client.common.SmallLabel;

/**
 * This widget is for setting fields on a bound fact or global variable.
 */
public class ActionSetFieldWidget extends RuleModellerWidget {

    final private ActionSetField model;
    final private DirtyableFlexTable layout;
    private boolean isBoundFact = false;
    private String[] fieldCompletions;
    private String variableClass;
    private boolean readOnly;

    private boolean isFactTypeKnown;

    public ActionSetFieldWidget( RuleModeller mod,
                                 EventBus eventBus,
                                 ActionSetField set,
                                 Boolean readOnly ) {
        super( mod,
               eventBus );
        this.model = set;
        this.layout = new DirtyableFlexTable();

        layout.setStyleName( "model-builderInner-Background" );

        PackageDataModelOracle completions = this.getModeller().getSuggestionCompletions();

        if ( completions.isGlobalVariable( set.getVariable() ) ) {
            this.fieldCompletions = completions.getFieldCompletionsForGlobalVariable( set.getVariable() );
            this.variableClass = completions.getGlobalVariable( set.getVariable() );
        } else {
            String type = mod.getModel().getLHSBindingType( set.getVariable() );
            if ( type != null ) {
                this.fieldCompletions = completions.getFieldCompletions( FieldAccessorsAndMutators.MUTATOR,
                                                                         type );
                this.variableClass = type;
                this.isBoundFact = true;
            } else {
                ActionInsertFact patternRhs = mod.getModel().getRHSBoundFact( set.getVariable() );
                if ( patternRhs != null ) {
                    this.fieldCompletions = completions.getFieldCompletions( FieldAccessorsAndMutators.MUTATOR,
                                                                             patternRhs.getFactType() );
                    this.variableClass = patternRhs.getFactType();
                    this.isBoundFact = true;
                }
            }
        }

        if ( this.variableClass == null ) {
            readOnly = true;
            ErrorPopup.showMessage( Constants.INSTANCE.CouldNotFindTheTypeForVariable0( set.getVariable() ) );
        }

        this.isFactTypeKnown = completions.isFactTypeRecognized( this.variableClass );
        if ( readOnly == null ) {
            this.readOnly = !this.isFactTypeKnown;
        } else {
            this.readOnly = readOnly;
        }

        if ( this.readOnly ) {
            layout.addStyleName( "editor-disabled-widget" );
        }

        doLayout();

        initWidget( this.layout );
    }

    private void doLayout() {
        layout.clear();

        for ( int i = 0; i < model.getFieldValues().length; i++ ) {
            ActionFieldValue val = model.getFieldValues()[ i ];

            layout.setWidget( i,
                              0,
                              getSetterLabel() );
            layout.setWidget( i,
                              1,
                              fieldSelector( val ) );
            layout.setWidget( i,
                              2,
                              valueEditor( val ) );
            final int idx = i;
            Image remove = GuidedRuleEditorImages508.INSTANCE.DeleteItemSmall();
            remove.addClickHandler( new ClickHandler() {

                public void onClick( ClickEvent event ) {
                    if ( Window.confirm( Constants.INSTANCE.RemoveThisItem() ) ) {
                        model.removeField( idx );
                        setModified( true );
                        getModeller().refreshWidget();

                        //Signal possible change in Template variables
                        TemplateVariablesChangedEvent tvce = new TemplateVariablesChangedEvent( getModeller().getModel() );
                        getEventBus().fireEventFromSource( tvce,
                                                           getModeller().getModel() );
                    }
                }
            } );
            if ( !this.readOnly ) {
                layout.setWidget( i,
                                  3,
                                  remove );
            }

        }

        if ( model.getFieldValues().length == 0 ) {
            HorizontalPanel h = new HorizontalPanel();
            h.add( getSetterLabel() );
            if ( !this.readOnly ) {
                Image image = GuidedRuleEditorImages508.INSTANCE.Edit();
                image.setAltText( Constants.INSTANCE.AddFirstNewField() );
                image.setTitle( Constants.INSTANCE.AddFirstNewField() );
                image.addClickHandler( new ClickHandler() {

                    public void onClick( ClickEvent sender ) {
                        showAddFieldPopup( sender );
                    }
                } );
                h.add( image );
            }
            layout.setWidget( 0,
                              0,
                              h );
        }

        //layout.setWidget( 0, 1, inner );

    }

    private Widget getSetterLabel() {

        ClickHandler clk = new ClickHandler() {

            public void onClick( ClickEvent event ) {
                //Widget w = (Widget)event.getSource();
                showAddFieldPopup( event );

            }
        };
        String modifyType = "set";
        if ( this.model instanceof ActionUpdateField) {
            modifyType = "modify";
        }

        String type = this.getModeller().getModel().getLHSBindingType( model.getVariable() );

        String descFact = ( type != null ) ? type + " <b>[" + model.getVariable() + "]</b>" : model.getVariable();

        String sl = Constants.INSTANCE.setterLabel( HumanReadable.getActionDisplayName(modifyType),
                                                    descFact );
        return new ClickableLabel( sl,
                                   clk,
                                   !this.readOnly );//HumanReadable.getActionDisplayName(modifyType) + " value of <b>[" + model.variable + "]</b>", clk);
    }

    protected void showAddFieldPopup( ClickEvent w ) {
        final PackageDataModelOracle completions = this.getModeller().getSuggestionCompletions();
        final FormStylePopup popup = new FormStylePopup( GuidedRuleEditorImages508.INSTANCE.Wizard(),
                                                         Constants.INSTANCE.AddAField() );

        final ListBox box = new ListBox();
        box.addItem( "..." );

        for ( int i = 0; i < fieldCompletions.length; i++ ) {
            box.addItem( fieldCompletions[ i ] );
        }

        box.setSelectedIndex( 0 );

        popup.addAttribute( Constants.INSTANCE.AddField(),
                            box );
        box.addChangeHandler( new ChangeHandler() {

            public void onChange( ChangeEvent event ) {
                String fieldName = box.getItemText( box.getSelectedIndex() );

                String fieldType = completions.getFieldType( variableClass,
                                                             fieldName );
                model.addFieldValue( new ActionFieldValue( fieldName,
                                                           "",
                                                           fieldType ) );
                setModified( true );
                getModeller().refreshWidget();
                popup.hide();
            }
        } );

        popup.show();

    }

    private Widget valueEditor( final ActionFieldValue val ) {
        PackageDataModelOracle completions = this.getModeller().getSuggestionCompletions();
        String type = "";
        if ( completions.isGlobalVariable( this.model.getVariable() ) ) {
            type = completions.getGlobalVariable( this.model.getVariable() );
        } else {
            type = this.getModeller().getModel().getLHSBindingType( this.model.getVariable() );
            /*
             * to take in account if the using a rhs bound variable
             */
            if ( type == null && !this.readOnly ) {
                type = this.getModeller().getModel().getRHSBoundFact( this.model.getVariable() ).getFactType();
            }
        }

        DropDownData enums = completions.getEnums( type,
                                                   val.getField(),
                                                   FieldNatureUtil.toMap( this.model.getFieldValues() ) );
        ActionValueEditor actionValueEditor = new ActionValueEditor( val,
                                                                     enums,
                                                                     this.getModeller(),
                                                                     this.getEventBus(),
                                                                     val.getType(),
                                                                     this.readOnly );
        actionValueEditor.setOnChangeCommand( new Command() {

            public void execute() {
                setModified( true );
            }
        } );
        return actionValueEditor;
    }

    private Widget fieldSelector( final ActionFieldValue val ) {
        return new SmallLabel( val.getField() );
    }

    /**
     * This returns true if the values being set are on a fact.
     */
    public boolean isBoundFact() {
        return isBoundFact;
    }

    public boolean isDirty() {
        return layout.hasDirty();
    }

    @Override
    public boolean isReadOnly() {
        return this.readOnly;
    }

    @Override
    public boolean isFactTypeKnown() {
        return this.isFactTypeKnown;
    }

}
