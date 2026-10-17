import { describe, expect, it, vi } from 'vitest';
import { fireEvent, render, screen, within } from '@testing-library/react';
import { MemoryRouter } from 'react-router-dom';
import { useState } from 'react';
import { Button } from '../Button';
import { TextField, ZoneTexte } from '../TextField';
import { Select } from '../Select';
import { Case, GroupeRadio, Interrupteur } from '../Choix';
import { Modal } from '../Modal';
import { Drawer } from '../Drawer';
import { Menu, MenuElement, MenuSeparateur } from '../Menu';
import { Onglets } from '../Onglets';
import { Tableau } from '../Tableau';
import { Message } from '../Message';
import { FilAriane } from '../FilAriane';
import { Lien } from '../Lien';
import { Badge } from '../Badge';

/** Lot L6b (E1) : comportement et accessibilite de la bibliotheque de composants. */

describe('Bouton', () => {
  it('chargement : desactive, aria-busy, libelle conserve', () => {
    render(<Button loading>Enregistrer</Button>);
    const b = screen.getByRole('button', { name: 'Enregistrer' });
    expect(b).toBeDisabled();
    expect(b).toHaveAttribute('aria-busy', 'true');
  });

  it('bouton icone : nom accessible par aria-label, icone decorative', () => {
    render(<Button iconOnly aria-label="Télécharger" icon={<svg data-testid="i" />} />);
    expect(screen.getByRole('button', { name: 'Télécharger' })).toBeInTheDocument();
    expect(screen.getByTestId('i').parentElement).toHaveAttribute('aria-hidden', 'true');
  });
});

describe('Champs', () => {
  it('libelle relie, aide puis erreur reliees par aria-describedby', () => {
    const { rerender } = render(<TextField label="ICE" hint="15 chiffres" />);
    const champ = screen.getByLabelText('ICE');
    expect(champ).toHaveAccessibleDescription('15 chiffres');
    rerender(<TextField label="ICE" hint="15 chiffres" error="ICE incomplet" />);
    expect(screen.getByLabelText('ICE')).toHaveAttribute('aria-invalid', 'true');
    expect(screen.getByLabelText('ICE')).toHaveAccessibleDescription('ICE incomplet');
    expect(screen.queryByText('15 chiffres')).not.toBeInTheDocument();
  });

  it('zone de texte et liste deroulante portent leur libelle', () => {
    render(
      <>
        <ZoneTexte label="Objet social" />
        <Select label="Forme" options={[{ value: 'SARL', label: 'SARL' }]} />
      </>,
    );
    expect(screen.getByLabelText('Objet social').tagName).toBe('TEXTAREA');
    expect(screen.getByLabelText('Forme').tagName).toBe('SELECT');
  });

  it('case, radio et interrupteur', () => {
    const surChoix = vi.fn();
    const surInter = vi.fn();
    render(
      <>
        <Case label="J’accepte" />
        <GroupeRadio legende="Rythme" name="r" value="m" onChange={surChoix} options={[{ value: 'm', label: 'Mensuel' }, { value: 'a', label: 'Annuel' }]} />
        <Interrupteur checked={false} onChange={surInter} label="Alertes" />
      </>,
    );
    expect(screen.getByRole('checkbox', { name: 'J’accepte' })).not.toBeChecked();
    expect(screen.getByRole('group', { name: 'Rythme' })).toBeInTheDocument();
    fireEvent.click(screen.getByRole('radio', { name: 'Annuel' }));
    expect(surChoix).toHaveBeenCalledWith('a');
    const sw = screen.getByRole('switch', { name: 'Alertes' });
    expect(sw).toHaveAttribute('aria-checked', 'false');
    fireEvent.click(sw);
    expect(surInter).toHaveBeenCalledWith(true);
  });
});

describe('Fenetres natives', () => {
  it('modale : <dialog> nomme par son titre, Echap et bouton Fermer ferment, pied d actions', () => {
    const fermer = vi.fn();
    render(
      <Modal open onOpenChange={fermer} title="Confirmer l’envoi" description="Le client recevra un courriel." footer={<Button>Envoyer</Button>}>
        <p>Corps</p>
      </Modal>,
    );
    const dlg = screen.getByRole('dialog', { name: 'Confirmer l’envoi' });
    expect(dlg.tagName).toBe('DIALOG');
    expect(dlg).toHaveAttribute('open');
    expect(dlg).toHaveAccessibleDescription('Le client recevra un courriel.');
    expect(within(dlg).getByRole('button', { name: 'Envoyer' })).toBeInTheDocument();
    fireEvent.keyDown(dlg, { key: 'Escape' });
    expect(fermer).toHaveBeenCalledWith(false);
    fireEvent.click(within(dlg).getByRole('button', { name: 'Fermer' }));
    expect(fermer).toHaveBeenCalledTimes(2);
  });

  it('modale fermee : rien n est rendu', () => {
    render(<Modal open={false} onOpenChange={() => undefined} title="X" />);
    expect(screen.queryByRole('dialog')).not.toBeInTheDocument();
  });

  it('clic sur le voile : ferme seulement si le clic commence et finit sur le voile', () => {
    const fermer = vi.fn();
    render(
      <Drawer open onClose={fermer} title="Tiroir">
        <p>Contenu</p>
      </Drawer>,
    );
    const dlg = screen.getByRole('dialog', { name: 'Tiroir' });
    fireEvent.mouseDown(screen.getByText('Contenu'));
    fireEvent.click(dlg);
    expect(fermer).not.toHaveBeenCalled();
    fireEvent.mouseDown(dlg);
    fireEvent.click(dlg);
    expect(fermer).toHaveBeenCalledTimes(1);
  });

  it('une fenetre imbriquee fermee par Echap ne ferme pas sa parente', () => {
    const parent = vi.fn();
    const enfant = vi.fn();
    render(
      <Drawer open onClose={parent} title="Parent">
        <Modal open onOpenChange={enfant} title="Enfant" />
      </Drawer>,
    );
    fireEvent.keyDown(screen.getByRole('dialog', { name: 'Enfant' }), { key: 'Escape' });
    expect(enfant).toHaveBeenCalledTimes(1);
    expect(parent).not.toHaveBeenCalled();
  });
});

describe('Menu', () => {
  function Exemple({ action }: { action: () => void }) {
    return (
      <MemoryRouter>
        <Menu libelle="Actions" bouton="Actions">
          <MenuElement onSelect={action}>Ouvrir</MenuElement>
          <MenuSeparateur />
          <MenuElement to="/profile">Profil</MenuElement>
        </Menu>
      </MemoryRouter>
    );
  }

  it('bouton de menu : ouvre, place le focus au clavier, fleches, Echap rend le focus', () => {
    const action = vi.fn();
    render(<Exemple action={action} />);
    const bouton = screen.getByRole('button', { name: 'Actions' });
    expect(bouton).toHaveAttribute('aria-expanded', 'false');
    fireEvent.keyDown(bouton, { key: 'ArrowDown' });
    const items = screen.getAllByRole('menuitem');
    expect(items).toHaveLength(2);
    expect(items[0]).toHaveFocus();
    fireEvent.keyDown(items[0], { key: 'ArrowDown' });
    expect(items[1]).toHaveFocus();
    expect(items[1]).toHaveAttribute('href', '/profile');
    fireEvent.keyDown(items[1], { key: 'Escape' });
    expect(screen.queryByRole('menu')).not.toBeInTheDocument();
    expect(bouton).toHaveFocus();
  });

  it('un choix execute l action et ferme ; un clic dehors ferme', () => {
    const action = vi.fn();
    render(<Exemple action={action} />);
    fireEvent.click(screen.getByRole('button', { name: 'Actions' }));
    fireEvent.click(screen.getByRole('menuitem', { name: 'Ouvrir' }));
    expect(action).toHaveBeenCalledTimes(1);
    expect(screen.queryByRole('menu')).not.toBeInTheDocument();
    fireEvent.click(screen.getByRole('button', { name: 'Actions' }));
    expect(screen.getByRole('menu')).toBeInTheDocument();
    fireEvent.pointerDown(document.body);
    expect(screen.queryByRole('menu')).not.toBeInTheDocument();
  });
});

describe('Onglets', () => {
  function Exemple() {
    const [actif, setActif] = useState('a');
    return (
      <Onglets
        libelle="Dossier"
        actif={actif}
        onChange={setActif}
        onglets={[
          { id: 'a', libelle: 'Infos', contenu: <p>Panneau A</p> },
          { id: 'b', libelle: 'Pièces', contenu: <p>Panneau B</p> },
        ]}
      />
    );
  }

  it('motif ARIA tabs : un seul onglet tabulable, fleches, panneau relie', () => {
    render(<Exemple />);
    const [a, b] = screen.getAllByRole('tab');
    expect(a).toHaveAttribute('aria-selected', 'true');
    expect(b).toHaveAttribute('tabindex', '-1');
    fireEvent.keyDown(a, { key: 'ArrowRight' });
    expect(screen.getByRole('tab', { name: 'Pièces' })).toHaveAttribute('aria-selected', 'true');
    expect(screen.getByRole('tabpanel', { name: 'Pièces' })).toHaveTextContent('Panneau B');
  });
});

describe('Tableau', () => {
  const colonnes = [
    { cle: 'nom', titre: 'Nom', triable: true, rendu: (l: { id: string; nom: string }) => l.nom },
    { cle: 'id', titre: 'Code', rendu: (l: { id: string; nom: string }) => l.id },
  ];

  it('en-tete trie : aria-sort et bouton de tri', () => {
    const surTri = vi.fn();
    render(<Tableau legende="Clients" colonnes={colonnes} lignes={[{ id: '1', nom: 'Alpha' }]} cleLigne={(l) => l.id} tri={{ cle: 'nom', sens: 'desc' }} onTri={surTri} />);
    expect(screen.getByRole('table', { name: 'Clients' })).toBeInTheDocument();
    expect(screen.getByRole('columnheader', { name: /Nom/ })).toHaveAttribute('aria-sort', 'descending');
    fireEvent.click(screen.getByRole('button', { name: /Nom/ }));
    expect(surTri).toHaveBeenCalledWith('nom');
    expect(screen.getByRole('cell', { name: 'Alpha' })).toBeInTheDocument();
  });

  it('chargement : tableau occupe (aria-busy), squelettes ; vide : le message de l appelant', () => {
    const { rerender } = render(<Tableau legende="T" colonnes={colonnes} lignes={[]} cleLigne={(l) => l.id} chargement />);
    expect(screen.getByRole('table')).toHaveAttribute('aria-busy', 'true');
    rerender(<Tableau legende="T" colonnes={colonnes} lignes={[]} cleLigne={(l) => l.id} vide={<p>Créez un client.</p>} />);
    expect(screen.getByText('Créez un client.')).toBeInTheDocument();
  });
});

describe('Message, fil d Ariane, lien, badge', () => {
  it('erreur en role alert, les autres en status', () => {
    render(
      <>
        <Message ton="erreur">Échec</Message>
        <Message ton="succes">Fait</Message>
      </>,
    );
    expect(screen.getByRole('alert')).toHaveTextContent('Échec');
    expect(screen.getByRole('status')).toHaveTextContent('Fait');
  });

  it('fil d Ariane : nav nommee, page courante sans lien', () => {
    render(
      <MemoryRouter>
        <FilAriane elements={[{ libelle: 'Accueil', to: '/dashboard' }, { libelle: 'Ticket' }]} />
      </MemoryRouter>,
    );
    const nav = screen.getByRole('navigation', { name: 'Fil d’Ariane' });
    expect(within(nav).getByRole('link', { name: 'Accueil' })).toHaveAttribute('href', '/dashboard');
    expect(within(nav).getByText('Ticket')).toHaveAttribute('aria-current', 'page');
  });

  it('lien externe : nouvel onglet signale ; badge : libelle porte l etat', () => {
    render(
      <>
        <Lien href="https://example.org">Site</Lien>
        <Badge variant="danger" dot>
          Bloqué
        </Badge>
      </>,
    );
    const a = screen.getByRole('link', { name: /Site/ });
    expect(a).toHaveAttribute('target', '_blank');
    expect(a).toHaveAttribute('rel', 'noopener noreferrer');
    expect(a).toHaveTextContent('nouvel onglet');
    expect(screen.getByText('Bloqué')).toBeInTheDocument();
  });
});
